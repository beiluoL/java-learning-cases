package com.enterprise.erpcrm.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.workflow.entity.WfRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流水 Mapper。
 *
 * @author erp-crm-system
 */
@Mapper
public interface WfRecordMapper extends BaseMapper<WfRecord> {
}
