package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户业务层。
 */
@Service
public class UserService {

    @Resource
    private UserMapper userMapper;

    /**
     * 根据主键查询用户。
     *
     * @param id 主键
     * @return 用户实体
     */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 查询用户列表。
     *
     * @return 用户列表
     */
    public List<User> listAll() {
        return userMapper.selectAll();
    }
}
