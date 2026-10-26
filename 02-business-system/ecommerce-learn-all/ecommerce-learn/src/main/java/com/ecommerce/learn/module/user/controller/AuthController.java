package com.ecommerce.learn.module.user.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.user.dto.*;
import com.ecommerce.learn.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（公开）：注册 / 登录 / 刷新令牌
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public Result<TokenPairVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public Result<TokenPairVO> refresh(@RequestParam String refreshToken) {
        return Result.success(userService.refresh(refreshToken));
    }
}
