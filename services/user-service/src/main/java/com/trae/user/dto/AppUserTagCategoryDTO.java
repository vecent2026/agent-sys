package com.trae.user.dto;

import lombok.Data;

@Data
public class AppUserTagCategoryDTO {
    private Long id;
    private String name;
    private String color;
    private String description;
    private Integer sort;
}
