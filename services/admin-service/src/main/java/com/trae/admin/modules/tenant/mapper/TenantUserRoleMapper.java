package com.trae.admin.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
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
}
