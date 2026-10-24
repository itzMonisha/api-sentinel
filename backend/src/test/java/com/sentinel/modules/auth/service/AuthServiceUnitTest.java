package com.sentinel.modules.auth.service;

import com.sentinel.modules.auth.dto.AuthResponse;
import com.sentinel.modules.auth.dto.LoginRequest;
import com.sentinel.modules.auth.dto.RegisterRequest;
import com.sentinel.modules.auth.dto.TokenRefreshRequest;
import com.sentinel.modules.auth.dto.UserResponse;
import com.sentinel.modules.auth.entity.RefreshToken;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.RefreshTokenRepository;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.shared.exception.ApiException;
import com.sentinel.shared.security.JwtTokenProvider;
import com.sentinel.shared.security.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RateLimiterService rateLimiterService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenProvider,
                rateLimiterService
        );
    }

    @Nested
    @DisplayName("Registration Unit Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should successfully register a new user with hashed password")
        void shouldRegisterSuccessfully() {
            RegisterRequest req = new RegisterRequest("newuser@apisentinel.dev", "Secret123!", "New User");
            when(userRepository.existsByEmail("newuser@apisentinel.dev")).thenReturn(false);
            when(passwordEncoder.encode("Secret123!")).thenReturn("$2a$12$hashedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse response = authService.register(req, "192.168.1.100");

            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("newuser@apisentinel.dev");
            assertThat(response.fullName()).isEqualTo("New User");
            assertThat(response.role()).isEqualTo(Role.ROLE_USER);
            assertThat(response.enabled()).isTrue();

            verify(rateLimiterService).checkRateLimit("192.168.1.100", "register");
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$12$hashedPassword");
        }

        @Test
        @DisplayName("Should reject registration when email already exists")
        void shouldRejectDuplicateEmail() {
            RegisterRequest req = new RegisterRequest("existing@apisentinel.dev", "Secret123!", "Existing User");
            when(userRepository.existsByEmail("existing@apisentinel.dev")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req, "192.168.1.100"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(apiEx.getErrorCode()).isEqualTo("DUPLICATE_EMAIL");
                    });

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Login Unit Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials and return JWT & refresh token")
        void shouldLoginSuccessfully() {
            LoginRequest req = new LoginRequest("user@apisentinel.dev", "Secret123!");
            User user = new User("user@apisentinel.dev", "$2a$12$hashed", "Test User", Role.ROLE_USER);

            when(userRepository.findByEmail("user@apisentinel.dev")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$12$hashed")).thenReturn(true);
            when(tokenProvider.generateAccessToken(user)).thenReturn("mock.access.token");
            when(tokenProvider.generateRefreshToken()).thenReturn("mock-raw-refresh-token");
            when(tokenProvider.hashToken("mock-raw-refresh-token")).thenReturn("mock-hashed-refresh-token");
            when(tokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
            when(tokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            AuthResponse response = authService.login(req, "10.0.0.1");

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo("mock.access.token");
            assertThat(response.refreshToken()).isEqualTo("mock-raw-refresh-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(900);
            assertThat(response.user().email()).isEqualTo("user@apisentinel.dev");

            verify(rateLimiterService).checkRateLimit("10.0.0.1", "login");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should reject login when email does not exist")
        void shouldRejectUnknownEmail() {
            LoginRequest req = new LoginRequest("unknown@apisentinel.dev", "Password123!");
            when(userRepository.findByEmail("unknown@apisentinel.dev")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req, "10.0.0.1"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(apiEx.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                    });
        }

        @Test
        @DisplayName("Should reject login when password mismatch occurs")
        void shouldRejectInvalidPassword() {
            LoginRequest req = new LoginRequest("user@apisentinel.dev", "WrongPassword!");
            User user = new User("user@apisentinel.dev", "$2a$12$hashed", "Test User", Role.ROLE_USER);

            when(userRepository.findByEmail("user@apisentinel.dev")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword!", "$2a$12$hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req, "10.0.0.1"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(apiEx.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                    });

            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should reject login when user account is disabled")
        void shouldRejectDisabledAccount() {
            LoginRequest req = new LoginRequest("disabled@apisentinel.dev", "Secret123!");
            User user = new User("disabled@apisentinel.dev", "$2a$12$hashed", "Disabled User", Role.ROLE_USER);
            user.setEnabled(false);

            when(userRepository.findByEmail("disabled@apisentinel.dev")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(req, "10.0.0.1"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(apiEx.getErrorCode()).isEqualTo("ACCOUNT_DISABLED");
                    });
        }
    }

    @Nested
    @DisplayName("Token Refresh Unit Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should rotate refresh token when presented with valid active token")
        void shouldRotateRefreshTokenSuccessfully() {
            TokenRefreshRequest req = new TokenRefreshRequest("raw-refresh-token-123");
            User user = new User("user@apisentinel.dev", "hash", "User", Role.ROLE_USER);
            RefreshToken activeToken = new RefreshToken(user, "hashed-token-123", Instant.now().plusSeconds(3600));

            when(tokenProvider.hashToken("raw-refresh-token-123")).thenReturn("hashed-token-123");
            when(refreshTokenRepository.findByTokenHash("hashed-token-123")).thenReturn(Optional.of(activeToken));
            when(tokenProvider.generateAccessToken(user)).thenReturn("new.access.token");
            when(tokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh-token");
            when(tokenProvider.hashToken("new-raw-refresh-token")).thenReturn("new-hashed-token");
            when(tokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
            when(tokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            AuthResponse response = authService.refreshToken(req);

            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo("new.access.token");
            assertThat(response.refreshToken()).isEqualTo("new-raw-refresh-token");

            assertThat(activeToken.isRevoked()).isTrue();
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should reject refresh when token is expired or revoked")
        void shouldRejectExpiredToken() {
            TokenRefreshRequest req = new TokenRefreshRequest("expired-token");
            User user = new User("user@apisentinel.dev", "hash", "User", Role.ROLE_USER);
            RefreshToken expiredToken = new RefreshToken(user, "hashed-expired", Instant.now().minusSeconds(10));

            when(tokenProvider.hashToken("expired-token")).thenReturn("hashed-expired");
            when(refreshTokenRepository.findByTokenHash("hashed-expired")).thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(apiEx.getErrorCode()).isEqualTo("REFRESH_TOKEN_EXPIRED");
                    });

            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }
    }
}
