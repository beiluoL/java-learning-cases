package com.enterprise.erpcrm.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.workflow.entity.WfInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例 Mapper。
 *
 * @author erp-crm-system
 */
@Mapper
public interface WfInstanceMapper extends BaseMapper<WfInstance> {
}
