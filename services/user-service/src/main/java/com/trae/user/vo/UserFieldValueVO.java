package com.trae.user.vo;

import lombok.Data;

@Data
public class UserFieldValueVO {
    private Long fieldId;
    private String fieldName;
    private String fieldKey;
    private String fieldType;
    private Object fieldValue;
    private String fieldValueLabel;
}
