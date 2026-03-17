package com.trae.admin.modules.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.platform.dto.TenantDto;
import com.trae.admin.modules.platform.dto.TenantQueryDto;
import com.trae.admin.modules.platform.entity.PlatformTenant;
import com.trae.admin.modules.platform.mapper.PlatformTenantMapper;
import com.trae.admin.modules.platform.service.PlatformTenantService;
import com.trae.admin.modules.platform.vo.TenantVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformTenantServiceImpl implements PlatformTenantService {

    private final PlatformTenantMapper tenantMapper;

    @Override
    public Page<TenantVo> page(TenantQueryDto query) {
        LambdaQueryWrapper<PlatformTenant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTenantName())) {
            wrapper.like(PlatformTenant::getTenantName, query.getTenantName());
        }
        if (StringUtils.hasText(query.getTenantCode())) {
            wrapper.like(PlatformTenant::getTenantCode, query.getTenantCode());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PlatformTenant::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(PlatformTenant::getCreateTime);

        Page<PlatformTenant> entityPage = tenantMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        Page<TenantVo> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public TenantVo get(Long id) {
        PlatformTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) throw new BusinessException("租户不存在");
        return toVo(tenant);
    }

    @Override
    @Transactional
    public void save(TenantDto dto) {
        Long count = tenantMapper.selectCount(new LambdaQueryWrapper<PlatformTenant>()
                .eq(PlatformTenant::getTenantCode, dto.getTenantCode()));
        if (count > 0) throw new BusinessException("租户编码已存在");

        PlatformTenant tenant = new PlatformTenant();
        BeanUtils.copyProperties(dto, tenant);
        tenant.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        tenant.setDataVersion(1);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.insert(tenant);
    }

    @Override
    @Transactional
    public void update(TenantDto dto) {
        PlatformTenant existing = tenantMapper.selectById(dto.getId());
        if (existing == null) throw new BusinessException("租户不存在");

        // 编码变更时检查唯一性
        if (!existing.getTenantCode().equals(dto.getTenantCode())) {
            Long count = tenantMapper.selectCount(new LambdaQueryWrapper<PlatformTenant>()
                    .eq(PlatformTenant::getTenantCode, dto.getTenantCode())
                    .ne(PlatformTenant::getId, dto.getId()));
            if (count > 0) throw new BusinessException("租户编码已存在");
        }

        BeanUtils.copyProperties(dto, existing);
        existing.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (tenantMapper.selectById(id) == null) throw new BusinessException("租户不存在");
        tenantMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        PlatformTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) throw new BusinessException("租户不存在");
        tenant.setStatus(status);
        // 状态变更时递增 dataVersion 使已有 JWT 失效
        tenant.setDataVersion(tenant.getDataVersion() + 1);
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
    }

    @Override
    public List<TenantVo> listAll() {
        return tenantMapper.selectList(new LambdaQueryWrapper<PlatformTenant>()
                        .eq(PlatformTenant::getStatus, 1)
                        .orderByAsc(PlatformTenant::getTenantName))
                .stream().map(this::toVo).collect(Collectors.toList());
    }

    private TenantVo toVo(PlatformTenant t) {
        TenantVo vo = new TenantVo();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }
}
