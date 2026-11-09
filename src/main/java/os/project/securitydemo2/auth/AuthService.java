package os.project.securitydemo2.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.project.securitydemo2.auth.dto.*;
import os.project.securitydemo2.common.ConflictException;
import os.project.securitydemo2.security.JwtService;
import os.project.securitydemo2.user.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwt;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        checkPasswordLength(request.password());
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        AppUser user = new AppUser();
        user.setName(request.name().strip());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Never trust a role supplied by someone registering an account.
        user.setRole(Role.USER);
        users.saveAndFlush(user);
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        checkPasswordLength(request.password());
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        return response(users.findByEmail(email).orElseThrow());
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(jwt.createToken(user), "Bearer",
                jwt.expiresInSeconds(), UserMapper.toResponse(user));
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static void checkPasswordLength(String password) {
        // BCrypt limits bytes, whereas @Size measures Java string length.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
    }
}

