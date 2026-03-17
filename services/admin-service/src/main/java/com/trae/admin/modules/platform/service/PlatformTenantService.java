package com.trae.admin.modules.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.modules.platform.dto.TenantDto;
import com.trae.admin.modules.platform.dto.TenantQueryDto;
import com.trae.admin.modules.platform.vo.TenantVo;

import java.util.List;

public interface PlatformTenantService {

    Page<TenantVo> page(TenantQueryDto query);

    TenantVo get(Long id);

    void save(TenantDto dto);

    void update(TenantDto dto);

    void delete(Long id);

    void changeStatus(Long id, Integer status);

    List<TenantVo> listAll();

    List<Long> getPermissionIds(Long tenantId);

    void updatePermissions(Long tenantId, List<Long> permissionIds);
}
