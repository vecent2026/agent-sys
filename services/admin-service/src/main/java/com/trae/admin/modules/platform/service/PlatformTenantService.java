package com.trae.admin.modules.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.modules.platform.dto.PlatformTenantCreateDto;
import com.trae.admin.modules.platform.dto.PlatformTenantQueryDto;
import com.trae.admin.modules.platform.dto.PlatformTenantUpdateDto;
import com.trae.admin.modules.platform.vo.PlatformTenantVo;

import java.util.List;

public interface PlatformTenantService {
    Page<PlatformTenantVo> page(PlatformTenantQueryDto queryDto);
    PlatformTenantVo get(Long id);
    void create(PlatformTenantCreateDto dto);
    void update(Long id, PlatformTenantUpdateDto dto);
    void updateStatus(Long id, Integer status);
    List<Long> getPermissionIds(Long tenantId);
    void updatePermissionIds(Long tenantId, List<Long> permissionIds);
}
