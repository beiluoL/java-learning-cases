package com.example.videostream.common;

import lombok.Getter;

/**
 * 业务错误码枚举（遵循「状态码 + 语义」的阿里规范风格）。
 * <p>
 * 规则：A 开头表示业务类错误；5 位码，前 2 位为模块（10=通用/上传，11=转码，12=存储，13=CDN/播放），后 3 位为序号。
 * </p>
 */
@Getter
public enum ErrorCode {

    // ===== 通用 10xxx =====
    SUCCESS("00000", "成功"),
    SYSTEM_ERROR("10000", "系统繁忙，请稍后重试"),
    PARAM_INVALID("10001", "参数校验失败"),
    RESOURCE_NOT_FOUND("10002", "资源不存在"),
    OPERATION_NOT_ALLOWED("10003", "当前状态不允许该操作"),

    // ===== 上传 11xxx =====
    UPLOAD_SESSION_NOT_FOUND("11001", "上传会话不存在或已过期"),
    UPLOAD_SESSION_EXPIRED("11002", "上传会话已过期"),
    UPLOAD_HASH_CONFLICT("11003", "文件哈希校验不一致，可能存在分片丢失"),
    UPLOAD_CHUNK_MISSING("11004", "存在缺失分片，无法完成合并"),

    // ===== 转码 12xxx =====
    TRANSCODE_TASK_NOT_FOUND("12001", "转码任务不存在"),
    TRANSCODE_FAILED("12002", "转码执行失败"),
    TRANSCODE_PROFILE_UNKNOWN("12003", "未知的清晰度档位"),
    MEDIA_NOT_READY("12004", "媒资尚未转码就绪，暂不可播放"),

    // ===== 存储 13xxx =====
    STORAGE_PRESIGN_FAILED("13001", "生成预签名上传地址失败"),
    STORAGE_OBJECT_NOT_FOUND("13002", "源文件不存在"),

    // ===== CDN / 播放 14xxx =====
    CDN_NO_AVAILABLE_NODE("14001", "当前区域无可用 CDN 节点"),
    URL_SIGN_FAILED("14002", "播放地址签名失败");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
