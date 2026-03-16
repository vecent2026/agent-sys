package com.trae.admin.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.admin.modules.tenant.entity.TenantPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantPermissionMapper extends BaseMapper<TenantPermission> {

    @Select("SELECT permission_id FROM tenant_permission WHERE tenant_id = #{tenantId}")
    List<Long> selectPermissionIdsByTenantId(@Param("tenantId") Long tenantId);
}
