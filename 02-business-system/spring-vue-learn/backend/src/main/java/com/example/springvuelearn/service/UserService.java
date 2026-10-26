package com.example.springvuelearn.service;

import com.example.springvuelearn.dto.UserCreateRequest;
import com.example.springvuelearn.entity.User;

import java.util.List;

/**
 * 用户服务接口。
 */
public interface UserService {

    /** 查询全部（逻辑删除的除外） */
    List<User> listAll();

    /** 新增用户 */
    User create(UserCreateRequest request);

    /** 按主键查询 */
    User getUser(Long id);
}
