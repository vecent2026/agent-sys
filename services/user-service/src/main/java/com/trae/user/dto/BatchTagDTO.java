package com.trae.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BatchTagDTO {
    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;

    @NotEmpty(message = "标签ID列表不能为空")
    private List<Long> tagIds;
}
