package os.project.securitydemo2.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public enum Role {
    USER(Set.of(Authority.PRODUCT_READ)),
    MANAGER(Set.of(Authority.PRODUCT_READ, Authority.PRODUCT_WRITE)),
    ADMIN(Set.of(Authority.PRODUCT_READ, Authority.PRODUCT_WRITE, Authority.PRODUCT_DELETE));

    private final Set<Authority> permissions;

    public List<SimpleGrantedAuthority> grantedAuthorities() {
        // hasRole("ADMIN") looks for ROLE_ADMIN; hasAuthority checks the exact text.
        return Stream.concat(Stream.of("ROLE_" + name()),
                        permissions.stream().map(Authority::getValue))
                .sorted().map(SimpleGrantedAuthority::new).toList();
    }
}

