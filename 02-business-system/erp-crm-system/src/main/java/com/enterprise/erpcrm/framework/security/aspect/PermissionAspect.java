package com.enterprise.erpcrm.framework.security.aspect;

import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.common.result.RCode;
import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.framework.security.annotation.Logical;
import com.enterprise.erpcrm.framework.security.annotation.RequiresPermissions;
import com.enterprise.erpcrm.framework.security.annotation.RequiresRoles;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 功能权限切面：在标注 {@link RequiresPermissions}/{@link RequiresRoles} 的方法执行前做鉴权。
 * <ul>
 *   <li>超级管理员（含 super-admin-role）直接放行；</li>
 *   <li>权限校验基于 {@link SecurityHolder} 中 {@code perms} 集合；</li>
 *   <li>角色校验基于 {@code roles} 集合；</li>
 *   <li>无权限抛出 {@link BizException}(FORBIDDEN)。</li>
 * </ul>
 *
 * @author erp-crm-system
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(com.enterprise.erpcrm.framework.security.annotation.RequiresPermissions)")
    public void checkPermissions(JoinPoint jp) {
        RequiresPermissions ann = getAnnotation(jp, RequiresPermissions.class);
        if (ann == null) {
            return;
        }
        if (isSuperAdmin()) {
            return;
        }
        Set<String> perms = currentPerms();
        boolean ok = ann.logical() == Logical.AND
                ? Arrays.stream(ann.value()).allMatch(perms::contains)
                : Arrays.stream(ann.value()).anyMatch(perms::contains);
        if (!ok) {
            throw new BizException(RCode.FORBIDDEN, "无权限：" + String.join(",", ann.value()));
        }
    }

    @Before("@annotation(com.enterprise.erpcrm.framework.security.annotation.RequiresRoles)")
    public void checkRoles(JoinPoint jp) {
        RequiresRoles ann = getAnnotation(jp, RequiresRoles.class);
        if (ann == null) {
            return;
        }
        if (isSuperAdmin()) {
            return;
        }
        Set<String> roles = currentRoles();
        boolean ok = ann.logical() == Logical.AND
                ? Arrays.stream(ann.value()).allMatch(roles::contains)
                : Arrays.stream(ann.value()).anyMatch(roles::contains);
        if (!ok) {
            throw new BizException(RCode.FORBIDDEN, "无角色权限：" + String.join(",", ann.value()));
        }
    }

    private boolean isSuperAdmin() {
        var u = SecurityHolder.get();
        return u != null && u.isSuperAdmin();
    }

    private Set<String> currentPerms() {
        var u = SecurityHolder.get();
        return u == null ? Set.of() : Set.copyOf(u.getPerms());
    }

    private Set<String> currentRoles() {
        var u = SecurityHolder.get();
        return u == null ? Set.of() : Set.copyOf(u.getRoles());
    }

    private <T extends java.lang.annotation.Annotation> T getAnnotation(JoinPoint jp, Class<T> type) {
        MethodSignature ms = (MethodSignature) jp.getSignature();
        return ms.getMethod().getAnnotation(type);
    }

    /** 占位：保留 List 导入供扩展统计用 */
    @SuppressWarnings("unused")
    private static List<String> empty() {
        return List.of();
    }
}
