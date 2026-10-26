package com.example.ssm.common.constant;

/**
 * 系统统一常量（集中管理魔法值，便于维护与二次开发时统一修改）。
 */
public final class Constants {

    private Constants() {
    }

    /* ========== 统一返回码（Layui 约定 code=0 表示成功） ========== */
    public static final int CODE_SUCCESS = 0;
    public static final int CODE_ERROR = 500;

    /* ========== 分页 ========== */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /* ========== 用户状态 ========== */
    public static final int USER_STATUS_ENABLED = 1;
    public static final int USER_STATUS_DISABLED = 0;

    /* ========== 用户默认密码（演示用，生产务必加密） ========== */
    public static final String DEFAULT_PASSWORD = "123456";
}
