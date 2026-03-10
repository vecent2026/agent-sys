package com.trae.user.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AppUserVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer gender;
    private String birthday;
    private String registerSource;
    private Integer status;
    private LocalDateTime registerTime;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private List<TagInfo> tags;
    private List<FieldValueInfo> fieldValues;

    @Data
    public static class TagInfo {
        private Long id;
        private String name;
        private String color;
    }

    @Data
    public static class FieldValueInfo {
        private Long fieldId;
        private String fieldKey;
        private String fieldName;
        private String fieldType;
        private Object fieldValue;
        private String fieldValueLabel;
    }
}
