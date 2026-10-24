package com.sentinel.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.dto.LoginRequest;
import com.sentinel.modules.auth.dto.RegisterRequest;
import com.sentinel.modules.auth.dto.TokenRefreshRequest;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.RefreshTokenRepository;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("User Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("POST /api/v1/auth/register creates user and returns 201 without password hash")
        void shouldRegisterUserSuccessfully() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "developer@apisentinel.dev",
                    "P@ssw0rd123!",
                    "Alex Sentinel"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.email", is("developer@apisentinel.dev")))
                    .andExpect(jsonPath("$.data.fullName", is("Alex Sentinel")))
                    .andExpect(jsonPath("$.data.role", is("ROLE_USER")))
                    .andExpect(jsonPath("$.data.enabled", is(true)))
                    .andExpect(jsonPath("$.data.id", notNullValue()))
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.password").doesNotExist());

            User user = userRepository.findByEmail("developer@apisentinel.dev").orElseThrow();
            assertThat(user.getFullName()).isEqualTo("Alex Sentinel");
            assertThat(passwordEncoder.matches("P@ssw0rd123!", user.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("POST /api/v1/auth/register rejects duplicate email with 409 Conflict")
        void shouldRejectDuplicateEmail() throws Exception {
            User existing = new User("duplicate@apisentinel.dev", passwordEncoder.encode("Secret1234!"), "Existing User", Role.ROLE_USER);
            userRepository.save(existing);

            RegisterRequest request = new RegisterRequest(
                    "duplicate@apisentinel.dev",
                    "DifferentPass123!",
                    "New User"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("DUPLICATE_EMAIL")))
                    .andExpect(jsonPath("$.error.status", is(409)))
                    .andExpect(jsonPath("$.error.detail", containsString("already exists")));
        }

        @Test
        @DisplayName("POST /api/v1/auth/register rejects weak password with 400 Validation Error")
        void shouldRejectWeakPassword() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "test@apisentinel.dev",
                    "short",
                    "Test User"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("VALIDATION_ERROR")))
                    .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("password")));
        }
    }

    @Nested
    @DisplayName("User Login & Authentication Tests")
    class LoginTests {

        @BeforeEach
        void createTestUser() {
            User user = new User("active@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "Active User", Role.ROLE_USER);
            userRepository.save(user);
        }

        @Test
        @DisplayName("POST /api/v1/auth/login authenticates valid credentials and returns JWT")
        void shouldLoginSuccessfully() throws Exception {
            LoginRequest request = new LoginRequest("active@apisentinel.dev", "ValidP@ss123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                    .andExpect(jsonPath("$.data.expiresIn", greaterThan(0)))
                    .andExpect(jsonPath("$.data.user.email", is("active@apisentinel.dev")))
                    .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("POST /api/v1/auth/login rejects incorrect password with 401 Unauthorized")
        void shouldRejectIncorrectPassword() throws Exception {
            LoginRequest request = new LoginRequest("active@apisentinel.dev", "WrongPassword123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("INVALID_CREDENTIALS")))
                    .andExpect(jsonPath("$.error.status", is(401)));
        }

        @Test
        @DisplayName("POST /api/v1/auth/login rejects non-existent email with 401 Unauthorized")
        void shouldRejectNonExistentEmail() throws Exception {
            LoginRequest request = new LoginRequest("nobody@apisentinel.dev", "ValidP@ss123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("INVALID_CREDENTIALS")));
        }

        @Test
        @DisplayName("POST /api/v1/auth/login rejects disabled account with 403 Forbidden")
        void shouldRejectDisabledAccount() throws Exception {
            User disabled = new User("disabled@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "Disabled User", Role.ROLE_USER);
            disabled.setEnabled(false);
            userRepository.save(disabled);

            LoginRequest request = new LoginRequest("disabled@apisentinel.dev", "ValidP@ss123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("ACCOUNT_DISABLED")));
        }
    }

    @Nested
    @DisplayName("Current User (GET /api/v1/auth/me) Tests")
    class CurrentUserTests {

        @Test
        @DisplayName("GET /api/v1/auth/me returns authenticated user details without password")
        void shouldReturnCurrentUserProfile() throws Exception {
            User user = new User("profile@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "Profile User", Role.ROLE_USER);
            User saved = userRepository.save(user);

            String token = jwtTokenProvider.generateAccessToken(saved);

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + token)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is(saved.getId().toString())))
                    .andExpect(jsonPath("$.data.email", is("profile@apisentinel.dev")))
                    .andExpect(jsonPath("$.data.fullName", is("Profile User")))
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @DisplayName("GET /api/v1/auth/me without token returns 401 Unauthorized")
        void shouldRejectUnauthenticatedMeRequest() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("UNAUTHORIZED")));
        }

        @Test
        @DisplayName("GET /api/v1/auth/me with malformed token returns 401 Unauthorized")
        void shouldRejectMalformedToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer malformed.jwt.token")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.error.errorCode", is("UNAUTHORIZED")));
        }

        @Test
        @DisplayName("User identity is strictly derived from JWT claims, preventing cross-user profile tampering (anti-IDOR)")
        void shouldIsolateUsersAndPreventCrossUserAccess() throws Exception {
            User userA = userRepository.save(new User("usera@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "User Alice", Role.ROLE_USER));
            User userB = userRepository.save(new User("userb@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "User Bob", Role.ROLE_USER));

            String tokenA = jwtTokenProvider.generateAccessToken(userA);
            String tokenB = jwtTokenProvider.generateAccessToken(userB);

            // Alice querying /me gets Alice's identity
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(userA.getId().toString())))
                    .andExpect(jsonPath("$.data.email", is("usera@apisentinel.dev")));

            // Bob querying /me gets Bob's identity
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(userB.getId().toString())))
                    .andExpect(jsonPath("$.data.email", is("userb@apisentinel.dev")));
        }
    }

    @Nested
    @DisplayName("Token Refresh & Rotation Tests")
    class TokenRefreshTests {

        @Test
        @DisplayName("POST /api/v1/auth/refresh rotates refresh token and invalidates old token")
        void shouldRotateRefreshTokenSuccessfully() throws Exception {
            User user = new User("refresh@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "Refresh User", Role.ROLE_USER);
            userRepository.save(user);

            // Step 1: Login to get initial refresh token
            LoginRequest loginReq = new LoginRequest("refresh@apisentinel.dev", "ValidP@ss123!");
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            String initialRefresh = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                    .path("data").path("refreshToken").asText();

            // Step 2: Refresh token
            TokenRefreshRequest refreshReq = new TokenRefreshRequest(initialRefresh);
            MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                    .andReturn();

            String newRefresh = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                    .path("data").path("refreshToken").asText();

            assertThat(newRefresh).isNotEqualTo(initialRefresh);

            // Step 3: Replaying old refresh token must be rejected (replay attack detection)
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new TokenRefreshRequest(initialRefresh))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.errorCode", is("REFRESH_TOKEN_REUSED")));
        }
    }

    @Nested
    @DisplayName("Logout & Session Termination Tests")
    class LogoutTests {

        @Test
        @DisplayName("POST /api/v1/auth/logout revokes active refresh token")
        void shouldLogoutAndRevokeToken() throws Exception {
            User user = new User("logout@apisentinel.dev", passwordEncoder.encode("ValidP@ss123!"), "Logout User", Role.ROLE_USER);
            userRepository.save(user);

            LoginRequest loginReq = new LoginRequest("logout@apisentinel.dev", "ValidP@ss123!");
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                    .path("data").path("accessToken").asText();
            String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                    .path("data").path("refreshToken").asText();

            // Execute logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new TokenRefreshRequest(refreshToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", containsString("Logged out successfully")));

            // Subsequent refresh using logged out token must fail
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new TokenRefreshRequest(refreshToken))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
