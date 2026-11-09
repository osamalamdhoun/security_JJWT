package os.project.securitydemo2.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import os.project.securitydemo2.common.PageResponse;
import os.project.securitydemo2.user.dto.RoleUpdateRequest;
import os.project.securitydemo2.user.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService users;

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return users.me(authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return users.list(page, size);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changeRole(@PathVariable @Min(1) long id,
                                  @Valid @RequestBody RoleUpdateRequest request) {
        return users.changeRole(id, request.role());
    }
}

