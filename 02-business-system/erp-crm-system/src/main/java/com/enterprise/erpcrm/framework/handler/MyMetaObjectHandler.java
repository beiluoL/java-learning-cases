package com.enterprise.erpcrm.framework.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.enterprise.erpcrm.common.constant.ErpConstants;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充：INSERT 填充 create_time / update_time / deleted，
 * UPDATE 仅填充 update_time。配合 {@code @TableField(fill=...)} 生效。
 *
 * @author erp-crm-system
 */
@Slf4j
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        // deleted 默认 0（逻辑未删除），仅当字段存在且为空时填充
        strictInsertFill(metaObject, "deleted", Integer.class, ErpConstants.DELETED_NO);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
