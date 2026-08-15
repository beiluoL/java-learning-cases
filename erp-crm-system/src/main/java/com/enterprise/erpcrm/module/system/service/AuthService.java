package com.enterprise.erpcrm.module.system.service;

import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.framework.security.LoginUser;
import com.enterprise.erpcrm.framework.security.PasswordUtils;
import com.enterprise.erpcrm.module.system.entity.SysUser;
import com.enterprise.erpcrm.module.system.domain.vo.RouterVo;
import java.util.List;

/**
 * 认证服务接口。
 *
 * @author erp-crm-system
 */
public interface AuthService {

    /**
     * 登录校验：校验用户名/密码，组装 LoginUser 并签发 JWT。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 生成的 JWT Token
     */
    String login(String username, String password);

    /**
     * 依据当前登录用户装配前端所需信息：用户资料、角色、权限、动态路由。
     *
     * @param userId 用户 id
     * @return LoginUser（含角色/权限/数据权限）与路由树
     */
    AuthInfo getInfo(Long userId);

    /**
     * 依据用户 id 重建 LoginUser（权限变更后刷新用）。
     *
     * @param userId 用户 id
     * @return LoginUser
     */
    LoginUser buildLoginUser(Long userId);

    /**
     * 登录信息聚合载体。
     */
    class AuthInfo {
        private LoginUser user;
        private List<RouterVo> routers;

        public AuthInfo(LoginUser user, List<RouterVo> routers) {
            this.user = user;
            this.routers = routers;
        }

        public LoginUser getUser() {
            return user;
        }

        public List<RouterVo> getRouters() {
            return routers;
        }
    }
}
