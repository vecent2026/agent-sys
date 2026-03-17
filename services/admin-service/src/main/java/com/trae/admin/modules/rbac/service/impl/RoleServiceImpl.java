package com.trae.admin.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import com.trae.admin.modules.rbac.entity.SysRole;
import com.trae.admin.modules.rbac.entity.SysRolePermission;
import com.trae.admin.modules.rbac.mapper.SysRoleMapper;
import com.trae.admin.modules.rbac.mapper.SysRolePermissionMapper;
import com.trae.admin.modules.rbac.service.RoleService;
import com.trae.admin.modules.rbac.vo.RoleVo;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final TenantUserRoleMapper tenantUserRoleMapper;

    @Override
    public Page<RoleVo> page(RoleQueryDto queryDto) {
        Page<SysRole> page = new Page<>(queryDto.getPage(), queryDto.getSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(queryDto.getRoleName())) {
            wrapper.like(SysRole::getRoleName, queryDto.getRoleName());
        }
        if (StringUtils.hasText(queryDto.getRoleKey())) {
            wrapper.like(SysRole::getRoleKey, queryDto.getRoleKey());
        }
        
        wrapper.orderByDesc(SysRole::getCreateTime);
        
        Page<SysRole> rolePage = sysRoleMapper.selectPage(page, wrapper);
        
        Page<RoleVo> resultPage = new Page<>();
        BeanUtils.copyProperties(rolePage, resultPage);
        
        List<RoleVo> list = rolePage.getRecords().stream().map(this::convertToVo).collect(Collectors.toList());
        setUserCounts(list);
        resultPage.setRecords(list);

        return resultPage;
    }

    @Override
    public List<RoleVo> listAll() {
        return sysRoleMapper.selectList(null).stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());
    }

    @Override
    public RoleVo get(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("Role not found");
        }
        return convertToVo(role);
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        return sysRolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
    }
    
    /**
     * Assign permissions to a role
     * @param roleId Role ID
     * @param permissionIds Permission ID list
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // Check if role exists
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("Role not found");
        }
        
        // Protect super admin role
        if ("admin".equals(role.getRoleKey())) {
            throw new BusinessException("超级管理员角色权限不可修改");
        }
        
        // Delete old permissions
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        
        // Save new permissions
        saveRolePermissions(roleId, permissionIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(RoleDto roleDto) {
        if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, roleDto.getRoleKey())) > 0) {
            throw new BusinessException("Role key already exists");
        }

        SysRole role = new SysRole();
        BeanUtils.copyProperties(roleDto, role);
        sysRoleMapper.insert(role);
        
        saveRolePermissions(role.getId(), roleDto.getPermissionIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleDto roleDto) {
        SysRole role = sysRoleMapper.selectById(roleDto.getId());
        if (role == null) {
            throw new BusinessException("Role not found");
        }

        // Protect super admin role
        if ("admin".equals(role.getRoleKey())) {
            throw new BusinessException("超级管理员角色不可修改");
        }

        if (!role.getRoleKey().equals(roleDto.getRoleKey())) {
            if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleKey, roleDto.getRoleKey())) > 0) {
                throw new BusinessException("Role key already exists");
            }
        }

        // Update only the fields that are allowed to be updated
        role.setRoleName(roleDto.getRoleName());
        role.setRoleKey(roleDto.getRoleKey());
        role.setDescription(roleDto.getDescription());
        
        sysRoleMapper.updateById(role);

        // Update permissions
        if (roleDto.getPermissionIds() != null) {
            sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, role.getId()));
            saveRolePermissions(role.getId(), roleDto.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        // Check if assigned to users
        if (tenantUserRoleMapper.selectCount(new LambdaQueryWrapper<TenantUserRole>()
                .in(TenantUserRole::getRoleId, ids)) > 0) {
            throw new BusinessException("Cannot delete role assigned to users");
        }

        // Protect super admin role
        List<SysRole> roles = sysRoleMapper.selectBatchIds(ids);
        for (SysRole role : roles) {
            if ("admin".equals(role.getRoleKey())) {
                throw new BusinessException("超级管理员角色不可删除");
            }
        }

        sysRoleMapper.deleteBatchIds(ids);
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, ids));
    }

    private void saveRolePermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permissionId);
                sysRolePermissionMapper.insert(rp);
            }
        }
    }

    private RoleVo convertToVo(SysRole role) {
        RoleVo vo = new RoleVo();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    private void setUserCounts(List<RoleVo> roleVos) {
        List<Map<String, Object>> userCounts = sysRoleMapper.selectRoleUserCount();
        for (Map<String, Object> count : userCounts) {
            Long roleId = Long.valueOf(count.get("id").toString());
            Integer userCount = count.get("user_count") != null ? Integer.valueOf(count.get("user_count").toString()) : 0;
            for (RoleVo vo : roleVos) {
                if (vo.getId().equals(roleId)) {
                    vo.setUserCount(userCount);
                    break;
                }
            }
        }
    }
}
