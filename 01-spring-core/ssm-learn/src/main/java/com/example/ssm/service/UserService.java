package com.example.ssm.service;

import com.example.ssm.common.result.PageResult;
import com.example.ssm.entity.User;

import java.util.List;

/**
 * 用户业务接口。面向接口编程，便于替换实现与单元测试。
 */
public interface UserService {

    /** 分页 + 条件查询
     * @param username 用户名（模糊）
     * @param status   状态（精确）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     */
    PageResult<User> pageList(String username, Integer status, int pageNum, int pageSize);

    /** 查询单条 */
    User getById(Long id);

    /** 总记录数 */
    int count();

    /** 新增 */
    void save(User user);

    /** 更新 */
    void update(User user);

    /** 删除单条 */
    void remove(Long id);

    /** 批量删除 */
    void removeBatch(List<Long> ids);
}
