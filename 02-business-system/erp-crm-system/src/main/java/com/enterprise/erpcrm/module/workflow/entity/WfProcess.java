package com.enterprise.erpcrm.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.enterprise.erpcrm.common.base.BaseEntity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程定义（wf_process）。
 * <p>一个流程由若干有序审批节点（{@link WfNode}）组成，节点通过 {@code nodeOrder} 升序流转，
 * 通过 {@code conditionExpr}（SpEL）实现条件进入，通过 {@code approveMode} 实现或签/会签。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("wf_process")
public class WfProcess extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程标识，代码中引用，如 PURCHASE_ORDER */
    private String processKey;
    /** 流程名称 */
    private String processName;
    /** 业务类型，与 ProcessCallback 实现类的 businessType 对应 */
    private String businessType;
    /** 流程分类（采购/销售/人事/合同） */
    private String category;
    /** 前端表单详情路由，审批时跳转查看业务单据 */
    private String formRoute;
    /** 版本号 */
    private Integer version;
    /** 状态：0 启用 1 停用 */
    private Integer status;
    /** 备注 */
    private String remark;

    /** 非持久化：节点列表（查看/保存流程定义时使用） */
    @TableField(exist = false)
    private List<WfNode> nodes;
}
