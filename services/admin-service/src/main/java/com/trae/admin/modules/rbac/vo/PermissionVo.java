package com.trae.admin.modules.rbac.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限VO
 */
@Data
public class PermissionVo {
    
    private Long id;
    private Long parentId;
    private String name;
    private String type;
    private String permissionKey;
    private String path;
    private String component;
    private Integer sort;
    private Boolean logEnabled;
    private String scope;
    private LocalDateTime createTime;

    /**
     * 子节点
     */
    private List<PermissionVo> children;
}
