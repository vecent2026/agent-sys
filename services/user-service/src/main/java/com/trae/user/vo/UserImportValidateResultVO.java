package com.trae.user.vo;

import lombok.Data;
import java.util.List;

@Data
public class UserImportValidateResultVO {
    private String taskId;
    private Integer total;
    private Integer validCount;
    private Integer invalidCount;
    private Boolean canProceed;
}
