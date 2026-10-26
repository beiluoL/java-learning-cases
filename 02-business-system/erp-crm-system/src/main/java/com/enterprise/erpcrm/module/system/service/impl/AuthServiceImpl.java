package com.enterprise.erpcrm.module.system.service.impl;

import com.enterprise.erpcrm.common.constant.ErpConstants;
import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.framework.security.LoginUser;
import com.enterprise.erpcrm.framework.security.PasswordUtils;
import com.enterprise.erpcrm.framework.security.JwtProvider;
import com.enterprise.erpcrm.module.system.domain.vo.RouterVo;
import com.enterprise.erpcrm.module.system.entity.SysUser;
import com.enterprise.erpcrm.module.system.service.AuthService;
import com.enterprise.erpcrm.module.system.service.SysMenuService;
import com.enterprise.erpcrm.module.system.service.SysRoleService;
import com.enterprise.erpcrm.module.system.service.SysUserService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final JwtProvider jwtProvider;

    @Override
    public String login(String username, String password) {
        SysUser user = userService.selectByUsername(username);
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException("账号已被停用");
        }
        if (!PasswordUtils.matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        LoginUser loginUser = buildLoginUser(user.getId());
        return jwtProvider.generateToken(loginUser.getUsername(), loginUser.getUserId(),
                loginUser.getDeptId(), loginUser.getDataScope(),
                loginUser.getRoles(), loginUser.getPerms());
    }

    @Override
    public AuthInfo getInfo(Long userId) {
        LoginUser user = buildLoginUser(userId);
        List<RouterVo> routers = menuService.selectRouterByUserId(userId);
        return new AuthInfo(user, routers);
    }

    @Override
    public LoginUser buildLoginUser(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        List<String> roleKeys = roleService.selectRoleKeysByUserId(userId);
        List<Long> roleIds = roleService.selectRoleIdsByUserId(userId);
        List<String> perms = roleService.selectPermsByRoleIds(roleIds);
        int dataScope = roleService.selectDataScopeByUserId(userId);
        boolean superAdmin = roleKeys.contains(ErpConstants.SUPER_ADMIN_ROLE_KEY)
                || perms.contains(ErpConstants.SUPER_ADMIN_PERM);
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .deptId(user.getDeptId())
                .roles(roleKeys)
                .perms(perms)
                .superAdmin(superAdmin)
                .dataScope(dataScope)
                .build();
    }
}
