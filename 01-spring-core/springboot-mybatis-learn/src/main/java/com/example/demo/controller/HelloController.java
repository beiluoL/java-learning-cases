package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 示例 REST 控制器。
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @Resource
    private UserService userService;

    /**
     * 健康检查 / 起步接口，不依赖数据库，用于验证项目启动与可访问性。
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new HashMap<String, Object>(4);
        result.put("code", 200);
        result.put("message", "Hello Spring Boot 2.6.x + MyBatis (JDK 1.8)!");
        return result;
    }

    /**
     * 查询用户列表（依赖 MySQL，需先建库建表并启动数据库）。
     */
    @GetMapping("/users")
    public List<User> listUsers() {
        return userService.listAll();
    }

    /**
     * 根据 ID 查询用户（依赖 MySQL）。
     */
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable("id") Long id) {
        return userService.getById(id);
    }
}
