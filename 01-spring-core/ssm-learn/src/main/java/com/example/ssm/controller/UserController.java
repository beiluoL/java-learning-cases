package com.example.ssm.controller;

import com.example.ssm.common.result.PageResult;
import com.example.ssm.common.result.Result;
import com.example.ssm.entity.User;
import com.example.ssm.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理 REST 接口。
 * 约定：/api/* 为后端接口，返回 JSON；页面通过 AJAX 调用。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /** 分页列表：GET /api/user/list?page=1&limit=10 */
    @GetMapping("/list")
    public PageResult<User> list(@RequestParam(required = false) String username,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int limit) {
        return userService.pageList(username, status, page, limit);
    }

    /** 详情：GET /api/user/{id} */
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    /** 新增：POST /api/user */
    @PostMapping
    public Result<Void> save(@RequestBody User user) {
        userService.save(user);
        return Result.success();
    }

    /** 更新：PUT /api/user */
    @PutMapping
    public Result<Void> update(@RequestBody User user) {
        userService.update(user);
        return Result.success();
    }

    /** 删除单条：DELETE /api/user/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.remove(id);
        return Result.success();
    }

    /** 批量删除：DELETE /api/user/batch，请求体为 id 数组 */
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        userService.removeBatch(ids);
        return Result.success();
    }
}
