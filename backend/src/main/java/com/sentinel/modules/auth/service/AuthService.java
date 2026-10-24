package com.sentinel.modules.auth.service;

import com.sentinel.modules.auth.dto.*;
import com.sentinel.modules.auth.entity.RefreshToken;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.RefreshTokenRepository;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.shared.exception.ApiException;
import com.sentinel.shared.security.JwtTokenProvider;
import com.sentinel.shared.security.RateLimiterService;
import com.sentinel.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RateLimiterService rateLimiterService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RateLimiterService rateLimiterService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.rateLimiterService = rateLimiterService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request, String clientIp) {
        rateLimiterService.checkRateLimit(clientIp, "register");

        String normalizedEmail = request.email().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected: duplicate email '{}' from IP {}", normalizedEmail, clientIp);
            throw new ApiException(
                    "An account with this email address already exists",
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EMAIL"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(normalizedEmail, passwordHash, request.fullName(), Role.ROLE_USER);
        User savedUser = userRepository.save(user);

        log.info("Registered new user [{}] with role [ROLE_USER]", savedUser.getId());
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        rateLimiterService.checkRateLimit(clientIp, "login");

        String normalizedEmail = request.email().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: email not found '{}' from IP {}", normalizedEmail, clientIp);
                    return new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
                });

        if (!user.isEnabled()) {
            log.warn("Login blocked: user account [{}] is disabled", user.getId());
            throw new ApiException("This account has been disabled. Please contact the administrator.", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: password mismatch for user [{}] from IP {}", user.getId(), clientIp);
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // Generate stateless JWT access token
        String accessToken = tokenProvider.generateAccessToken(user);

        // Generate and persist cryptographically random refresh token
        String rawRefreshToken = tokenProvider.generateRefreshToken();
        String hashedToken = tokenProvider.hashToken(rawRefreshToken);
        Instant refreshExpiry = Instant.now().plusMillis(tokenProvider.getRefreshExpirationMs());

        RefreshToken refreshToken = new RefreshToken(user, hashedToken, refreshExpiry);
        refreshTokenRepository.save(refreshToken);

        log.info("User [{}] successfully authenticated from IP {}", user.getId(), clientIp);

        long expiresInSeconds = tokenProvider.getAccessExpirationMs() / 1000;
        return AuthResponse.of(accessToken, rawRefreshToken, expiresInSeconds, UserResponse.fromEntity(user));
    }

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        String hashedToken = tokenProvider.hashToken(request.refreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        User user = refreshToken.getUser();

        // Check if token was previously revoked (replay attack detection)
        if (refreshToken.isRevoked()) {
            log.error("SECURITY ALERT: Revoked refresh token reused for user [{}]! Revoking all active user sessions.", user.getId());
            refreshTokenRepository.revokeAllUserTokens(user);
            throw new ApiException(
                    "Refresh token has been revoked. All user sessions have been terminated for security.",
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REUSED"
            );
        }

        if (refreshToken.isExpired()) {
            throw new ApiException("Refresh token has expired. Please log in again.", HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED");
        }

        if (!user.isEnabled()) {
            throw new ApiException("This account has been disabled.", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        // Refresh Token Rotation: Revoke current token and issue new token pair
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRawRefreshToken = tokenProvider.generateRefreshToken();
        String newHashedToken = tokenProvider.hashToken(newRawRefreshToken);
        Instant newExpiry = Instant.now().plusMillis(tokenProvider.getRefreshExpirationMs());

        RefreshToken newRefreshTokenEntity = new RefreshToken(user, newHashedToken, newExpiry);
        refreshTokenRepository.save(newRefreshTokenEntity);

        log.info("Rotated refresh token for user [{}]", user.getId());

        long expiresInSeconds = tokenProvider.getAccessExpirationMs() / 1000;
        return AuthResponse.of(newAccessToken, newRawRefreshToken, expiresInSeconds, UserResponse.fromEntity(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException("Not authenticated", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException("User profile not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void logout(String rawRefreshToken, UserPrincipal principal) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hashedToken = tokenProvider.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(hashedToken).ifPresent(RefreshToken::revoke);
        } else if (principal != null) {
            userRepository.findById(principal.getId())
                    .ifPresent(refreshTokenRepository::revokeAllUserTokens);
        }
        log.info("User session logged out successfully");
    }
}
