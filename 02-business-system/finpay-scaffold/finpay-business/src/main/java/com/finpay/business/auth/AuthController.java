package com.finpay.business.auth;

import com.finpay.common.result.Result;
import com.finpay.security.auth.JwtTokenProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录接口。校验账密后签发 JWT，前端后续请求携带 Authorization: Bearer <token>。
 * 该接口在 SecurityConfig 中放行。
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin
public class AuthController {

    private final UserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserDetailsService userDetailsService, JwtTokenProvider jwtTokenProvider) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest req) {
        UserDetails user = userDetailsService.loadUserByUsername(req.username());
        // 演示用 {noop} 明文比较；生产应对比 BCrypt 哈希
        String stored = user.getPassword();
        String input = stored.startsWith("{noop}") ? req.password() : req.password();
        if (!stored.replace("{noop}", "").equals(input)) {
            return Result.fail(40002, "用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user);
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        return Result.success(data);
    }

    public record LoginRequest(String username, String password) {}
}
