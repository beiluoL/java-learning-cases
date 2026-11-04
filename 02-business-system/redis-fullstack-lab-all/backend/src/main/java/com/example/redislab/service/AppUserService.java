package com.example.redislab.service;

import com.example.redislab.entity.AppUser;

public interface AppUserService {
    /** 校验用户名密码，返回用户（教学用明文比对，生产请用加密） */
    AppUser login(String username, String password);
}
