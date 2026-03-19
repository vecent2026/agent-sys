package com.trae.admin.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantUserRoleMapper extends BaseMapper<TenantUserRole> {

    @Select("SELECT role_id FROM tenant_user_role WHERE user_id = #{userId} AND tenant_id = #{tenantId}")
    List<Long> selectRoleIdsByUserAndTenant(Long userId, Long tenantId);

    @Delete("DELETE FROM tenant_user_role WHERE user_id = #{userId} AND tenant_id = #{tenantId}")
    void deleteByUserAndTenant(Long userId, Long tenantId);

    @Delete("DELETE FROM tenant_user_role WHERE user_id = #{userId} AND tenant_id = #{tenantId} AND role_id = #{roleId}")
    void deleteByUserTenantRole(Long userId, Long tenantId, Long roleId);

    @Select("SELECT COUNT(1) > 0 " +
            "FROM tenant_user_role tur " +
            "INNER JOIN tenant_role tr ON tr.id = tur.role_id AND tr.tenant_id = tur.tenant_id " +
            "WHERE tur.user_id = #{userId} AND tur.tenant_id = #{tenantId} " +
            "  AND tr.is_deleted = 0 AND (tr.is_super = 1 OR tr.role_key = 'tenant_admin')")
    boolean existsSuperRoleByUserAndTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(DISTINCT tur.user_id) " +
            "FROM tenant_user_role tur " +
            "INNER JOIN tenant_role tr ON tr.id = tur.role_id AND tr.tenant_id = tur.tenant_id " +
            "WHERE tur.tenant_id = #{tenantId} " +
            "  AND tr.is_deleted = 0 AND (tr.is_super = 1 OR tr.role_key = 'tenant_admin')")
    long countSuperAdminsByTenant(@Param("tenantId") Long tenantId);

    @Select("SELECT id FROM tenant_role " +
            "WHERE tenant_id = #{tenantId} AND is_deleted = 0 " +
            "  AND (is_super = 1 OR role_key = 'tenant_admin') " +
            "ORDER BY is_super DESC, is_builtin DESC, id ASC LIMIT 1")
    Long selectDefaultSuperRoleId(@Param("tenantId") Long tenantId);

    @Select("SELECT id FROM tenant_role " +
            "WHERE tenant_id = #{tenantId} AND is_deleted = 0 " +
            "  AND (is_super = 1 OR role_key = 'tenant_admin')")
    List<Long> selectSuperRoleIdsByTenant(@Param("tenantId") Long tenantId);

    @Insert("INSERT INTO tenant_role(tenant_id, role_name, role_key, description, is_builtin, is_super, create_time, update_time, is_deleted) " +
            "VALUES(#{tenantId}, '租户超级管理员', 'tenant_admin', '内置租户超管角色，不可删除/编辑', 1, 1, NOW(), NOW(), 0)")
    int insertDefaultSuperRole(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(1) FROM tenant_user_role WHERE user_id = #{userId} AND tenant_id = #{tenantId} AND role_id = #{roleId}")
    long countUserRoleBinding(@Param("userId") Long userId, @Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
