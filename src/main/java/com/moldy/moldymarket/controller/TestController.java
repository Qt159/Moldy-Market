package com.moldy.moldymarket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Kiểm thử (Test)", description = "API kiểm tra hệ thống và xác thực JWT")
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestController {

    @Operation(summary = "Kiểm tra endpoint bảo vệ", description = "Kiểm tra token xác thực JWT của người dùng hiện tại và trả về danh sách quyền hạn")
    @GetMapping("/protected")
    public Map<String, Object> protectedEndpoint(
            @Parameter(hidden = true) Authentication authentication
    ) {
        return Map.of(
                "message", "JWT authentication is working!",
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }
}