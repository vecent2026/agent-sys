package com.trae.user.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class UserImportDTO {
    @ExcelProperty(value = "昵称", index = 0)
    private String nickname;

    @ExcelProperty(value = "手机号", index = 1)
    private String mobile;

    @ExcelProperty(value = "邮箱", index = 2)
    private String email;

    @ExcelProperty(value = "性别", index = 3)
    private String gender;

    @ExcelProperty(value = "生日", index = 4)
    private String birthday;

    @ExcelProperty(value = "状态", index = 5)
    private String status;

    @ExcelProperty(value = "标签", index = 6)
    private String tags;

    private Integer rowIndex;
    private String errorMsg;
    private boolean valid = true;
}
