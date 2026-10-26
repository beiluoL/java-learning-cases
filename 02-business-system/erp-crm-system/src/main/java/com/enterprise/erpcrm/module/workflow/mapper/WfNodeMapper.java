package com.enterprise.erpcrm.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.workflow.entity.WfNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程节点定义 Mapper。
 *
 * @author erp-crm-system
 */
@Mapper
public interface WfNodeMapper extends BaseMapper<WfNode> {
}
