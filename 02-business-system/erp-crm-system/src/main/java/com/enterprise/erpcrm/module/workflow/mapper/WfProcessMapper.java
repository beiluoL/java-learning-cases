package com.enterprise.erpcrm.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.workflow.entity.WfProcess;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程定义 Mapper。
 *
 * @author erp-crm-system
 */
@Mapper
public interface WfProcessMapper extends BaseMapper<WfProcess> {
}
