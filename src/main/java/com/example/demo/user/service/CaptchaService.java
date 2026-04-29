package com.example.demo.user.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.user.dto.UserDtos.LoginCaptchaResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private static final String SVG_TEMPLATE = """
        <svg xmlns="http://www.w3.org/2000/svg" width="132" height="48" viewBox="0 0 132 48">
            <rect width="132" height="48" rx="16" fill="#082f49"/>
            <text x="50%%" y="54%%" dominant-baseline="middle" text-anchor="middle"
                  font-family="Arial, sans-serif" font-size="22" font-weight="700"
                  letter-spacing="4" fill="#e0f2fe">%s</text>
        </svg>
        """;

    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();
    private final String testCaptchaCode;

    public CaptchaService(@Value("${app.security.captcha.test-code:}") String testCaptchaCode) {
        this.testCaptchaCode = testCaptchaCode == null ? "" : testCaptchaCode.trim().toUpperCase();
    }

    public LoginCaptchaResponse createCaptcha() {
        String captchaId = "captcha_" + UUID.randomUUID();
        String captchaCode = testCaptchaCode.isBlank()
            ? UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase()
            : testCaptchaCode;
        captchaStore.put(captchaId, new CaptchaEntry(captchaCode, Instant.now().plusSeconds(300)));
        return new LoginCaptchaResponse(captchaId, toSvgDataUri(captchaCode));
    }

    public void validateCaptcha(String captchaId, String captchaCode) {
        CaptchaEntry entry = captchaStore.get(captchaId);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new BusinessException(400, "captcha expired");
        }

        if (!entry.code().equalsIgnoreCase(captchaCode)) {
            throw new BusinessException(400, "captcha invalid");
        }

        captchaStore.remove(captchaId);
    }

    private String toSvgDataUri(String captchaCode) {
        String svg = SVG_TEMPLATE.formatted(captchaCode);
        String encoded = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + encoded;
    }

    private record CaptchaEntry(String code, Instant expiresAt) {
    }
}
