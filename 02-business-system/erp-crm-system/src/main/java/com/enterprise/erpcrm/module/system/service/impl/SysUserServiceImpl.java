package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.framework.security.PasswordUtils;
import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.framework.security.datascope.DataScopeContext;
import com.enterprise.erpcrm.module.system.domain.query.SysUserQuery;
import com.enterprise.erpcrm.module.system.entity.SysUser;
import com.enterprise.erpcrm.module.system.mapper.SysUserMapper;
import com.enterprise.erpcrm.module.system.service.SysUserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统用户服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public SysUser selectByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public boolean checkUsernameUnique(Long userId, String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (Objects.nonNull(userId)) {
            wrapper.ne(SysUser::getId, userId);
        }
        return count(wrapper) == 0;
    }

    /**
     * 新增用户：密码 BCrypt 加密，写入审计人。
     *
     * @param entity 用户实体（含明文 password）
     * @return 是否成功
     */
    @Override
    public boolean save(SysUser entity) {
        if (!checkUsernameUnique(entity.getId(), entity.getUsername())) {
            throw new BizException("用户名已存在");
        }
        if (Objects.isNull(entity.getPassword())) {
            entity.setPassword(PasswordUtils.encode("123456"));
        } else {
            entity.setPassword(PasswordUtils.encode(entity.getPassword()));
        }
        fillOperator(entity);
        return super.save(entity);
    }

    /**
     * 更新用户：密码留空则不改；用户名变更做唯一校验。
     *
     * @param entity 用户实体
     * @return 是否成功
     */
    @Override
    public boolean updateById(SysUser entity) {
        if (!checkUsernameUnique(entity.getId(), entity.getUsername())) {
            throw new BizException("用户名已存在");
        }
        if (Objects.isNull(entity.getPassword()) || entity.getPassword().isEmpty()) {
            entity.setPassword(null);
        } else {
            entity.setPassword(PasswordUtils.encode(entity.getPassword()));
        }
        fillOperator(entity);
        return super.updateById(entity);
    }

    private void fillOperator(SysUser entity) {
        String operator = SecurityHolder.hasLogin() ? SecurityHolder.get().getUsername() : "system";
        if (entity.getId() == null) {
            entity.setCreateBy(operator);
        }
        entity.setUpdateBy(operator);
    }

    @Override
    public IPage<SysUser> selectUserPage(IPage<SysUser> page, SysUserQuery query) {
        return baseMapper.selectUserPage(page, query, DataScopeContext.get());
    }
}
