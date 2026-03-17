package com.trae.admin.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.tenant.entity.TenantRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantRolePermissionMapper extends BaseMapper<TenantRolePermission> {

    /**
     * 查询用户在某租户内拥有的权限 key（通过角色关联）
     */
    @Select("SELECT DISTINCT p.permission_key " +
            "FROM platform_permission p " +
            "INNER JOIN tenant_role_permission trp ON p.id = trp.permission_id " +
            "INNER JOIN tenant_user_role tur ON trp.role_id = tur.role_id AND trp.tenant_id = tur.tenant_id " +
            "WHERE tur.user_id = #{userId} AND tur.tenant_id = #{tenantId} " +
            "  AND p.is_deleted = 0")
    List<String> selectUserPermissionKeys(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Select("SELECT permission_id FROM tenant_role_permission WHERE role_id = #{roleId} AND tenant_id = #{tenantId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM tenant_role_permission WHERE role_id = #{roleId} AND tenant_id = #{tenantId}")
    void deleteByRoleId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);
}
