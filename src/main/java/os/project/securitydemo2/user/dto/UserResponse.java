package os.project.securitydemo2.user.dto;

import os.project.securitydemo2.user.Role;
import java.util.List;

// A response DTO deliberately has no password or passwordHash field.
public record UserResponse(Long id, String name, String email, Role role, List<String> authorities) {}

