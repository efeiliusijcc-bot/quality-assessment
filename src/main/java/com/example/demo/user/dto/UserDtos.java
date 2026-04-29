package com.example.demo.user.dto;

import jakarta.validation.constraints.NotBlank;

public final class UserDtos {

    private UserDtos() {
    }

    public record LoginCaptchaResponse(String captchaId, String captchaImage) {
    }

    public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password,
        @NotBlank(message = "captchaId is required") String captchaId,
        @NotBlank(message = "captchaCode is required") String captchaCode
    ) {
    }

    public record UserProfileResponse(String id, String name, String role) {
    }

    public record LoginResponse(String token, UserProfileResponse user) {
    }
}
