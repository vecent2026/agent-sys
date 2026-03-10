package com.trae.user.model;

import lombok.Data;

@Data
public class ImportTemplateModel {
    @com.alibaba.excel.annotation.ExcelProperty("昵称")
    private String nickname;

    @com.alibaba.excel.annotation.ExcelProperty("手机号")
    private String mobile;

    @com.alibaba.excel.annotation.ExcelProperty("邮箱")
    private String email;

    @com.alibaba.excel.annotation.ExcelProperty("性别")
    private String gender;

    @com.alibaba.excel.annotation.ExcelProperty("生日")
    private String birthday;

    @com.alibaba.excel.annotation.ExcelProperty("状态")
    private String status;

    @com.alibaba.excel.annotation.ExcelProperty("标签")
    private String tags;
}
