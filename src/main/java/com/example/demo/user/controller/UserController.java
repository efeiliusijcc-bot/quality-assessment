package com.example.demo.user.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.user.dto.UserDtos.LoginCaptchaResponse;
import com.example.demo.user.dto.UserDtos.LoginRequest;
import com.example.demo.user.dto.UserDtos.LoginResponse;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.CaptchaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final CaptchaService captchaService;
    private final AuthService authService;

    public UserController(CaptchaService captchaService, AuthService authService) {
        this.captchaService = captchaService;
        this.authService = authService;
    }

    @GetMapping("/captcha")
    public ApiResponse<LoginCaptchaResponse> getCaptcha() {
        return ApiResponse.success(captchaService.createCaptcha());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "login success");
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.success(authService.logout(), "logout success");
    }
}
