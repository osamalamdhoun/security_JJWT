package os.project.securitydemo2.auth.dto;

import os.project.securitydemo2.user.dto.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}

