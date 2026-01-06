package com.trae.admin.modules.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trae.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父ID
     */
    private Long parentId;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 类型(DIR/MENU/BTN)
     */
    private String type;

    /**
     * 权限标识
     */
    private String permissionKey;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 排序号
     */
    private Integer sort;
    
    /**
     * 是否开启日志记录
     */
    private Boolean logEnabled;
}