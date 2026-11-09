package os.project.securitydemo2.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.project.securitydemo2.auth.AuthService;
import os.project.securitydemo2.user.AppUser;
import os.project.securitydemo2.user.UserRepository;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = users.findByEmail(AuthService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new User(user.getEmail(), user.getPasswordHash(), user.getRole().grantedAuthorities());
    }
}

