package com.example.demo.user.service;

import com.example.demo.app.domain.AppUser;
import com.example.demo.app.repository.AppUserRepository;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.security.AuthUser;
import com.example.demo.security.JwtTokenService;
import com.example.demo.user.dto.UserDtos.LoginRequest;
import com.example.demo.user.dto.UserDtos.LoginResponse;
import com.example.demo.user.dto.UserDtos.UserProfileResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final CaptchaService captchaService;

    public AuthService(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        CaptchaService captchaService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.captchaService = captchaService;
    }

    public LoginResponse login(LoginRequest request) {
        captchaService.validateCaptcha(request.captchaId(), request.captchaCode());

        AppUser user = appUserRepository.findByUsername(request.username())
            .orElseThrow(() -> new BusinessException(401, "username or password invalid"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "username or password invalid");
        }

        AuthUser authUser = new AuthUser(user);
        return new LoginResponse(
            jwtTokenService.generateToken(authUser),
            new UserProfileResponse(user.getUserId().toString(), user.getRealName(), user.getRoleCode())
        );
    }

    public boolean logout() {
        return true;
    }
}
