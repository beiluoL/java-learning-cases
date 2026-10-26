package com.example.springvuelearn.controller;

import com.example.springvuelearn.dto.UserCreateRequest;
import com.example.springvuelearn.entity.User;
import com.example.springvuelearn.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口示例。
 * GET  /api/users      列表
 * POST /api/users      新增
 * GET  /api/users/{id} 详情
 */
@RestController
@RequestMapping("/api/users")
public class SysUserController {

    private final UserService userService;

    public SysUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> list() {
        return userService.listAll();
    }

    @PostMapping
    public User create(@RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return userService.getUser(id);
    }
}
