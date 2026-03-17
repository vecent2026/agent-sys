package com.trae.admin.modules.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantDto {
    private Long id;

    @NotBlank(message = "租户编码不能为空")
    @Size(min = 2, max = 64, message = "租户编码长度须在2~64之间")
    @Pattern(regexp = "^[a-z0-9_-]+$", message = "租户编码只允许小写字母、数字、下划线、连字符")
    private String tenantCode;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称不超过128个字符")
    private String tenantName;

    private Integer status;

    private LocalDateTime expireTime;

    @Min(value = 1, message = "最大用户数至少为1")
    @Max(value = 100000, message = "最大用户数不超过100000")
    private Integer maxUsers;
}
