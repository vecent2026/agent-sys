package com.trae.admin.modules.log.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

/**
 * 操作日志 ES 文档实体
 */
@Data
@Document(indexName = "sys_log")
public class SysLogDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String traceId;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Keyword)
    private String module;

    @Field(type = FieldType.Keyword)
    private String action;

    @Field(type = FieldType.Ip)
    private String ip;

    @Field(type = FieldType.Text)
    private String params;

    @Field(type = FieldType.Text, index = false)
    private String result;

    @Field(type = FieldType.Text)
    private String errorMsg;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long costTime;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Date createTime;
}
