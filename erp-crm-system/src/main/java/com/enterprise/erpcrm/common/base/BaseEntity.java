package com.enterprise.erpcrm.common.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体基类：所有业务表实体统一继承，固化「主键 + 审计字段 + 逻辑删除」三件套。
 * <p>
 * 对应数据库规范（见 db/mysql/schema.sql 表头约定）：
 * <ul>
 *   <li>{@code id}          主键，自增</li>
 *   <li>{@code create_time} 创建时间，插入时由 {@code MyMetaObjectHandler} 自动填充</li>
 *   <li>{@code update_time} 更新时间，插入/更新时自动填充</li>
 *   <li>{@code deleted}     逻辑删除（全局配置 {@code logic-delete-field=deleted}）</li>
 * </ul>
 * </p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
