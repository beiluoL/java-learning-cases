package com.example.redislab.modules.session;

import com.example.redislab.common.R;
import com.example.redislab.entity.AppUser;
import com.example.redislab.service.AppUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景2：分布式会话（Spring Session 存 Redis）。
 *
 * <p>@EnableRedisHttpSession 已开启：HttpSession 不再存在 Tomcat 内存，而是序列化进 Redis
 * （key 形如 spring:session:sessions:<sessionId>）。因此多实例部署时，任意实例写入的登录态，
 * 其它实例都能读到——前端无需感知后端有几台机器。</p>
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final AppUserService appUserService;

    public SessionController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @PostMapping("/login")
    public R<String> login(@RequestParam String username,
                           @RequestParam String password,
                           HttpSession session) {
        AppUser user = appUserService.login(username, password);
        // 写入会话（实际被 Spring Session 存进 Redis）
        session.setAttribute("loginUser", user);
        session.setAttribute("uid", user.getId());
        return R.ok("登录成功，sessionId=" + session.getId());
    }

    @GetMapping("/me")
    public R<Object> me(HttpSession session) {
        Object user = session.getAttribute("loginUser");
        if (user == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(user);
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpSession session) {
        session.invalidate();
        return R.ok();
    }

    /** 返回当前会话在 Redis 中的存放信息，配合前端「原始结构」展示 */
    @GetMapping("/raw")
    public R<Map<String, Object>> raw(HttpSession session) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sessionId", session.getId());
        info.put("redisKey", "spring:session:sessions:" + session.getId());
        info.put("uid", session.getAttribute("uid"));
        info.put("loginUser", session.getAttribute("loginUser"));
        return R.ok(info);
    }
}
