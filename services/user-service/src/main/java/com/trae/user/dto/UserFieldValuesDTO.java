package com.trae.user.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserFieldValuesDTO {
    private List<FieldValueItem> fieldValues;

    @Data
    public static class FieldValueItem {
        private Long fieldId;
        private Object fieldValue;
    }
}
