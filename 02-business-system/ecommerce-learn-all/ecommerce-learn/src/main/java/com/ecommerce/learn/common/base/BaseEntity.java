package com.ecommerce.learn.common.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：所有业务表共用字段
 * <ul>
 *   <li>id：雪花算法（ASSIGN_ID），分布式环境安全</li>
 *   <li>createdAt / updatedAt：自动填充</li>
 *   <li>deleted：逻辑删除</li>
 * </ul>
 */
@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
