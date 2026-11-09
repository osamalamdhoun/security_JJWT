package os.project.securitydemo2.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.project.securitydemo2.common.PageResponse;
import os.project.securitydemo2.common.ResourceNotFoundException;
import os.project.securitydemo2.user.dto.UserResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;

    public UserResponse me(String email) {
        return UserMapper.toResponse(users.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    public PageResponse<UserResponse> list(int page, int size) {
        Page<UserResponse> result = users.findAll(PageRequest.of(page, size, Sort.by("id")))
                .map(UserMapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional
    public UserResponse changeRole(long id, Role role) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRole(role);
        return UserMapper.toResponse(user);
    }
}

