package com.enterprise.erpcrm.framework.security.annotation;

/**
 * 操作类型枚举：与 sys_dict_data(sys_oper_type) 取值对齐。
 *
 * @author erp-crm-system
 */
public enum BusinessType {
    OTHER(0, "其它"),
    INSERT(1, "新增"),
    UPDATE(2, "修改"),
    DELETE(3, "删除"),
    EXPORT(4, "导出"),
    IMPORT(5, "导入"),
    APPROVE(6, "审批"),
    QUERY(7, "查询"),
    GRANT(8, "授权"),
    RESET_PWD(9, "重置密码"),
    CLEAN(10, "清空");

    private final int value;
    private final String desc;

    BusinessType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }
}
