package com.enterprise.erpcrm.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批任务 Mapper。
 *
 * @author erp-crm-system
 */
@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {
}
