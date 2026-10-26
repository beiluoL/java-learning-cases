package com.example.springvuelearn.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springvuelearn.dto.UserCreateRequest;
import com.example.springvuelearn.entity.User;
import com.example.springvuelearn.mapper.UserMapper;
import com.example.springvuelearn.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务实现。继承 ServiceImpl 直接获得 BaseMapper 的 CRUD。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public List<User> listAll() {
        return list();
    }

    @Override
    public User create(UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        save(user);
        return user;
    }

    @Override
    public User getUser(Long id) {
        return super.getById(id);
    }
}
