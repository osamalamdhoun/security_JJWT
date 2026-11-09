package os.project.securitydemo2.user.dto;

import jakarta.validation.constraints.NotNull;
import os.project.securitydemo2.user.Role;

public record RoleUpdateRequest(@NotNull Role role) {}

