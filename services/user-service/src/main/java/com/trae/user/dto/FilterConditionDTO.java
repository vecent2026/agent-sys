package com.trae.user.dto;

import lombok.Data;

@Data
public class FilterConditionDTO {
    private String id;
    private String field;
    private String operator;
    private Object value;
    private String type;
}