package com.example.redislab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.redislab.entity.AppUser;
import com.example.redislab.mapper.AppUserMapper;
import com.example.redislab.service.AppUserService;
import org.springframework.stereotype.Service;

@Service
public class AppUserServiceImpl implements AppUserService {

    private final AppUserMapper appUserMapper;

    public AppUserServiceImpl(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public AppUser login(String username, String password) {
        AppUser user = appUserMapper.selectOne(new QueryWrapper<AppUser>().eq("username", username));
        if (user == null || !user.getPassword().equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return user;
    }
}
