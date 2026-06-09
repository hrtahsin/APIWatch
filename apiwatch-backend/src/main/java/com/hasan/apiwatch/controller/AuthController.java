package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.AuthUserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    AuthUserResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("VIEWER");
        return new AuthUserResponse(authentication.getName(), role);
    }
}
