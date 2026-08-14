package com.example.ssm.service.impl;

import com.example.ssm.common.constant.Constants;
import com.example.ssm.common.exception.BusinessException;
import com.example.ssm.common.result.PageResult;
import com.example.ssm.mapper.UserMapper;
import com.example.ssm.entity.User;
import com.example.ssm.service.UserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 用户业务实现类。
 * @Service 标记为 Spring 业务 Bean；@Transactional 声明式事务（遇异常自动回滚）。
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<User> pageList(String username, Integer status, int pageNum, int pageSize) {
        // ① 开启分页：紧邻查询语句前调用，仅对接下来的一条查询生效
        PageHelper.startPage(pageNum, pageSize);
        List<User> list = userMapper.selectByCondition(username, status);
        // ② 用 PageInfo 取出总数
        PageInfo<User> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getTotal(), list);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public int count() {
        return userMapper.count();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(User user) {
        Date now = new Date();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        if (user.getStatus() == null) {
            user.setStatus(Constants.USER_STATUS_ENABLED);
        }
        if (StringUtils.isEmpty(user.getPassword())) {
            user.setPassword(Constants.DEFAULT_PASSWORD); // 演示用默认密码，真实项目务必加密
        }
        userMapper.insert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(User user) {
        User exist = userMapper.selectById(user.getId());
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }
        user.setUpdateTime(new Date());
        userMapper.update(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        userMapper.deleteBatch(ids);
    }
}
