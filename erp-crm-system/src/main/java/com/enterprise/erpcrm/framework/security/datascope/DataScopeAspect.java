package com.enterprise.erpcrm.framework.security.datascope;

import com.enterprise.erpcrm.common.constant.DataScopeType;
import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.framework.security.annotation.DataScope;
import com.enterprise.erpcrm.framework.security.LoginUser;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面：在标注 {@link DataScope} 的列表查询方法执行前，依据当前用户的数据权限范围，
 * 计算 SQL 过滤片段并存入 {@link DataScopeContext}；方法返回后清理。
 * <p>支持五档范围：全部 / 自定义部门 / 本部门 / 本部门及以下 / 仅本人。</p>
 *
 * @author erp-crm-system
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DeptDataRepository deptDataRepository;

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        LoginUser user = SecurityHolder.get();
        String fragment = null;
        if (user != null && !user.isSuperAdmin()) {
            fragment = buildFragment(user, dataScope);
        }
        DataScopeContext.set(fragment);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    private String buildFragment(LoginUser user, DataScope ann) {
        DataScopeType type = DataScopeType.of(user.getDataScope());
        Long userId = user.getUserId();
        Long deptId = user.getDeptId();
        String deptAlias = ann.deptAlias();
        String userAlias = ann.userAlias();
        switch (type) {
            case ALL:
                return null;
            case CUSTOM:
                List<Long> custom = deptDataRepository.selectCustomDeptIds(userId);
                return inFragment(deptAlias, ann.deptColumn(), custom);
            case DEPT:
                return String.format(" AND %s.%s = %d", deptAlias, ann.deptColumn(), deptId);
            case DEPT_AND_CHILD:
                List<Long> child = deptDataRepository.selectChildDeptIds(deptId);
                return inFragment(deptAlias, ann.deptColumn(), child);
            case SELF:
                return String.format(" AND %s.%s = %d", userAlias, ann.userColumn(), userId);
            default:
                return null;
        }
    }

    private String inFragment(String alias, String column, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            // 无可见部门：构造恒不成立条件，避免越权看到全部
            return " AND 1 = 0";
        }
        StringJoiner joiner = new StringJoiner(",");
        ids.forEach(id -> joiner.add(String.valueOf(id)));
        return String.format(" AND %s.%s IN (%s)", alias, column, joiner);
    }
}
