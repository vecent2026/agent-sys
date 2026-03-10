package com.trae.user.model;

import lombok.Data;

@Data
public class ExportUserModel {
    @com.alibaba.excel.annotation.ExcelProperty("ID")
    private Long id;
    
    @com.alibaba.excel.annotation.ExcelProperty("昵称")
    private String nickname;
    
    @com.alibaba.excel.annotation.ExcelProperty("手机号")
    private String mobile;
    
    @com.alibaba.excel.annotation.ExcelProperty("邮箱")
    private String email;
    
    @com.alibaba.excel.annotation.ExcelProperty("性别")
    private String gender;
    
    @com.alibaba.excel.annotation.ExcelProperty("注册来源")
    private String registerSource;
    
    @com.alibaba.excel.annotation.ExcelProperty("状态")
    private String status;
    
    @com.alibaba.excel.annotation.ExcelProperty("注册时间")
    private java.util.Date registerTime;
    
    @com.alibaba.excel.annotation.ExcelProperty("最后登录时间")
    private java.util.Date lastLoginTime;
    
    @com.alibaba.excel.annotation.ExcelProperty("最后登录IP")
    private String lastLoginIp;
    
    @com.alibaba.excel.annotation.ExcelProperty("标签")
    private String tags;
}
