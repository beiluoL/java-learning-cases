package com.enterprise.erpcrm.module.system.controller;

import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.LoginUser;
import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.module.system.domain.form.LoginForm;
import com.enterprise.erpcrm.module.system.domain.vo.RouterVo;
import com.enterprise.erpcrm.module.system.service.AuthService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：登录、获取用户信息、退出。
 *
 * @author erp-crm-system
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录：校验用户名密码并返回 JWT。
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginForm form) {
        String token = authService.login(form.getUsername(), form.getPassword());
        Map<String, String> data = new HashMap<>(2);
        data.put("token", token);
        return Result.success(data);
    }

    /**
     * 获取当前登录用户信息、角色、权限与动态路由。
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        LoginUser user = SecurityHolder.get();
        if (user == null) {
            return Result.error(com.enterprise.erpcrm.common.result.RCode.UNAUTHORIZED, "未登录");
        }
        AuthService.AuthInfo authInfo = authService.getInfo(user.getUserId());
        List<RouterVo> routers = authInfo.getRouters();
        Map<String, Object> data = new HashMap<>(4);
        data.put("user", authInfo.getUser());
        data.put("roles", authInfo.getUser().getRoles());
        data.put("permissions", authInfo.getUser().getPerms());
        data.put("menus", routers);
        return Result.success(data);
    }

    /**
     * 退出：前端清理本地 token 即可，后端无状态。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
