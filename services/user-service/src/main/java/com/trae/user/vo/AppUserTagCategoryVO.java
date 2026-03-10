package com.trae.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AppUserTagCategoryVO {
    private Long id;
    private String name;
    private String color;
    private String description;
    private Integer sort;
    private Integer tagCount;
    private LocalDateTime createTime;
}
