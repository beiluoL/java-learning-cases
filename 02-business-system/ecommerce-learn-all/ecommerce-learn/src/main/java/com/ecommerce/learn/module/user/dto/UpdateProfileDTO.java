package com.ecommerce.learn.module.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新个人资料请求（非必填字段均可为空）
 */
@Data
public class UpdateProfileDTO {

    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String email;

    private String avatar;
}
