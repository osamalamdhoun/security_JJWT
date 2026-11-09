package os.project.securitydemo2.user;

import org.springframework.security.core.GrantedAuthority;
import os.project.securitydemo2.user.dto.UserResponse;

public final class UserMapper {
    private UserMapper() {}

    public static UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getRole().grantedAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList());
    }
}

