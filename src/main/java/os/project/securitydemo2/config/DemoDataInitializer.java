package os.project.securitydemo2.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import os.project.securitydemo2.auth.AuthService;
import os.project.securitydemo2.user.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoDataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DemoProperties properties;

    @Override
    @Transactional
    public void run(String... args) {
        create("Demo User", properties.getUserEmail(), properties.getUserPassword(), Role.USER);
        create("Demo Manager", properties.getManagerEmail(), properties.getManagerPassword(), Role.MANAGER);
        create("Demo Admin", properties.getAdminEmail(), properties.getAdminPassword(), Role.ADMIN);
    }

    private void create(String name, String email, String password, Role role) {
        String normalized = AuthService.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            return;
        }
        AuthService.checkPasswordLength(password);
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(normalized);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        users.save(user);
    }
}

