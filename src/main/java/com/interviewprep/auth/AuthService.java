package com.interviewprep.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.interviewprep.auth.dto.LoginRequest;
import com.interviewprep.auth.dto.RegisterRequest;
import com.interviewprep.auth.dto.TokenResponse;
import com.interviewprep.common.exception.ConflictException;
import com.interviewprep.common.exception.FieldValidationException;
import com.interviewprep.user.AppUser;
import com.interviewprep.user.Role;
import com.interviewprep.user.UserRepository;
import com.interviewprep.user.UserService;
import com.interviewprep.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes of input; longer passwords are rejected rather than silently truncated. */
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtTokenService jwtTokenService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
    }

    /** Always creates a USER; ADMIN accounts cannot be self-registered. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new FieldValidationException("password", "must be at most " + BCRYPT_MAX_BYTES + " bytes");
        }
        String email = UserService.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), Role.USER, createdAt);
        try {
            // Flush now so a concurrent registration of the same email hits the unique constraint here.
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this email already exists");
        }
    }

    /**
     * Checks the credentials with Spring Security's AuthenticationManager (BCrypt comparison), then issues a token.
     * Unknown email and wrong password produce the same message, so the response does not reveal which emails are
     * registered.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        String email = UserService.normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (InternalAuthenticationServiceException ex) {
            throw ex; // infrastructure failure, e.g. database down: surfaces as a logged 500, not a misleading 401
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));
        return jwtTokenService.issueToken(user);
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}
