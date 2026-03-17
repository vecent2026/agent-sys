package com.trae.admin.modules.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.platform.dto.PlatformTenantCreateDto;
import com.trae.admin.modules.platform.dto.PlatformTenantQueryDto;
import com.trae.admin.modules.platform.dto.PlatformTenantUpdateDto;
import com.trae.admin.modules.platform.entity.PlatformTenant;
import com.trae.admin.modules.platform.mapper.PlatformTenantMapper;
import com.trae.admin.modules.platform.service.PlatformTenantService;
import com.trae.admin.modules.platform.vo.PlatformTenantVo;
import com.trae.admin.modules.tenant.entity.TenantPermission;
import com.trae.admin.modules.tenant.mapper.TenantPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformTenantServiceImpl implements PlatformTenantService {

    private final PlatformTenantMapper platformTenantMapper;
    private final TenantPermissionMapper tenantPermissionMapper;

    @Override
    public Page<PlatformTenantVo> page(PlatformTenantQueryDto queryDto) {
        Page<PlatformTenant> page = new Page<>(queryDto.getPage(), queryDto.getSize());
        LambdaQueryWrapper<PlatformTenant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDto.getKeyword())) {
            wrapper.and(w -> w.like(PlatformTenant::getTenantName, queryDto.getKeyword())
                    .or().like(PlatformTenant::getTenantCode, queryDto.getKeyword()));
        }
        if (queryDto.getStatus() != null) {
            wrapper.eq(PlatformTenant::getStatus, queryDto.getStatus());
        }
        wrapper.orderByDesc(PlatformTenant::getCreateTime);
        Page<PlatformTenant> result = platformTenantMapper.selectPage(page, wrapper);
        Page<PlatformTenantVo> voPage = new Page<>();
        BeanUtils.copyProperties(result, voPage);
        voPage.setRecords(result.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public PlatformTenantVo get(Long id) {
        PlatformTenant entity = platformTenantMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("租户不存在");
        }
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(PlatformTenantCreateDto dto) {
        if (platformTenantMapper.selectCount(new LambdaQueryWrapper<PlatformTenant>()
                .eq(PlatformTenant::getTenantCode, dto.getTenantCode())) > 0) {
            throw new BusinessException("租户编码已存在");
        }
        PlatformTenant entity = new PlatformTenant();
        entity.setTenantCode(dto.getTenantCode());
        entity.setTenantName(dto.getTenantName());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        entity.setMaxUsers(dto.getMaxUsers() != null ? dto.getMaxUsers() : 100);
        entity.setDataVersion(0);
        if (StringUtils.hasText(dto.getExpireTime())) {
            String s = dto.getExpireTime().replace("Z", "").replace(" ", "T");
            if (s.length() > 19) s = s.substring(0, 19);
            entity.setExpireTime(LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        platformTenantMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PlatformTenantUpdateDto dto) {
        PlatformTenant entity = platformTenantMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("租户不存在");
        }
        if (StringUtils.hasText(dto.getTenantName())) {
            entity.setTenantName(dto.getTenantName());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getMaxUsers() != null) {
            entity.setMaxUsers(dto.getMaxUsers());
        }
        if (dto.getExpireTime() != null) {
            if (dto.getExpireTime().isEmpty()) {
                entity.setExpireTime(null);
            } else {
                String s = dto.getExpireTime().replace("Z", "").replace(" ", "T");
                if (s.length() > 19) s = s.substring(0, 19);
                entity.setExpireTime(LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        platformTenantMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        PlatformTenant entity = platformTenantMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("租户不存在");
        }
        entity.setStatus(status);
        entity.setDataVersion(entity.getDataVersion() + 1);
        platformTenantMapper.updateById(entity);
    }

    @Override
    public List<Long> getPermissionIds(Long tenantId) {
        return tenantPermissionMapper.selectPermissionIdsByTenantId(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermissionIds(Long tenantId, List<Long> permissionIds) {
        tenantPermissionMapper.delete(new LambdaQueryWrapper<TenantPermission>()
                .eq(TenantPermission::getTenantId, tenantId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permId : permissionIds) {
                TenantPermission tp = new TenantPermission();
                tp.setTenantId(tenantId);
                tp.setPermissionId(permId);
                tenantPermissionMapper.insert(tp);
            }
        }
    }

    private PlatformTenantVo toVo(PlatformTenant entity) {
        PlatformTenantVo vo = new PlatformTenantVo();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
