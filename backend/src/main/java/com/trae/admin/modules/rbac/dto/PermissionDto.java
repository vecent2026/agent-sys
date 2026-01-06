package com.trae.admin.modules.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限DTO
 */
@Data
public class PermissionDto {
    
    private Long id;
    private Long parentId;

    /**
     * 节点名称
     */
    @NotBlank(message = "节点名称不能为空")
    @Size(min = 2, max = 20, message = "节点名称长度必须在2-20个字符之间")
    private String name;

    /**
     * 类型(DIR/MENU/BTN)
     */
    @NotBlank(message = "类型不能为空")
    private String type;

    /**
     * 权限标识
     */
    @Size(max = 100, message = "权限标识长度不能超过100个字符")
    private String permissionKey;

    /**
     * 路由地址
     */
    @Size(max = 200, message = "路由地址长度不能超过200个字符")
    private String path;

    /**
     * 组件路径
     */
    @Size(max = 200, message = "组件路径长度不能超过200个字符")
    private String component;

    /**
     * 排序号
     */
    @NotNull(message = "排序号不能为空")
    private Integer sort;
    
    /**
     * 是否开启日志记录
     */
    private Boolean logEnabled = true;
}
