package com.trae.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class AppUserTagDTO {
    private Long id;

    private Long categoryId;

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过50字符")
    private String name;

    @Size(max = 200, message = "描述不能超过200字符")
    private String description;

    private Integer status;

    private List<Long> tagIds;
}
