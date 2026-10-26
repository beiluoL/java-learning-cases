package com.ecommerce.learn.module.user.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.user.dto.UpdateProfileDTO;
import com.ecommerce.learn.module.user.dto.UserVO;
import com.ecommerce.learn.module.user.service.UserService;
import com.ecommerce.learn.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户中心（需登录）
 */
@Tag(name = "用户中心")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取个人资料")
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.success(userService.profile(UserContext.getUserId()));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.success(userService.updateProfile(UserContext.getUserId(), dto));
    }
}
