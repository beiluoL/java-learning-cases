package com.example.videostream.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 云转码回调请求：云厂商（MediaConvert / MTS）完成某档位转码后回传的结果。
 */
@Data
public class TranscodeCallbackRequest implements Serializable {

    @NotBlank(message = "taskId 不能为空")
    private String taskId;

    /** 该档位播放列表在对象存储的 key（成功时必填） */
    private String playlistKey;

    private boolean success;

    /** 失败原因 */
    private String errorMessage;
}
