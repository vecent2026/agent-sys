package com.trae.admin.modules.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.platform.entity.PlatformRole;
import com.trae.admin.modules.platform.entity.PlatformRolePermission;
import com.trae.admin.modules.platform.mapper.PlatformRoleMapper;
import com.trae.admin.modules.platform.mapper.PlatformRolePermissionMapper;
import com.trae.admin.modules.platform.service.PlatformRoleService;
import com.trae.admin.modules.platform.vo.PlatformRoleVo;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import com.trae.admin.modules.user.entity.SysUserRole;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
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
public class PlatformRoleServiceImpl implements PlatformRoleService {

    private final PlatformRoleMapper platformRoleMapper;
    private final PlatformRolePermissionMapper platformRolePermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public Page<PlatformRoleVo> page(RoleQueryDto queryDto) {
        Page<PlatformRole> page = new Page<>(queryDto.getPage(), queryDto.getSize());
        LambdaQueryWrapper<PlatformRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(PlatformRole::getIsDeleted, 0).or().isNull(PlatformRole::getIsDeleted));
        if (StringUtils.hasText(queryDto.getRoleName())) {
            wrapper.like(PlatformRole::getRoleName, queryDto.getRoleName());
        }
        if (StringUtils.hasText(queryDto.getRoleKey())) {
            wrapper.like(PlatformRole::getRoleKey, queryDto.getRoleKey());
        }
        wrapper.orderByDesc(PlatformRole::getCreateTime);
        Page<PlatformRole> rolePage = platformRoleMapper.selectPage(page, wrapper);
        Page<PlatformRoleVo> resultPage = new Page<>();
        BeanUtils.copyProperties(rolePage, resultPage);
        List<PlatformRoleVo> list = rolePage.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        setUserCounts(list);
        resultPage.setRecords(list);
        return resultPage;
    }

    @Override
    public List<PlatformRoleVo> listAll() {
        return platformRoleMapper.selectList(
                new LambdaQueryWrapper<PlatformRole>()
                        .and(w -> w.eq(PlatformRole::getIsDeleted, 0).or().isNull(PlatformRole::getIsDeleted)))
                .stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public PlatformRoleVo get(Long id) {
        PlatformRole role = platformRoleMapper.selectById(id);
        if (role == null || (role.getIsDeleted() != null && role.getIsDeleted() == 1)) {
            throw new BusinessException("角色不存在");
        }
        return toVo(role);
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        return platformRolePermissionMapper.selectList(
                new LambdaQueryWrapper<PlatformRolePermission>().eq(PlatformRolePermission::getRoleId, roleId))
                .stream().map(PlatformRolePermission::getPermissionId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        PlatformRole role = platformRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        platformRolePermissionMapper.delete(new LambdaQueryWrapper<PlatformRolePermission>()
                .eq(PlatformRolePermission::getRoleId, roleId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permId : permissionIds) {
                PlatformRolePermission rp = new PlatformRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permId);
                platformRolePermissionMapper.insert(rp);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(RoleDto roleDto) {
        if (platformRoleMapper.selectCount(new LambdaQueryWrapper<PlatformRole>()
                .eq(PlatformRole::getRoleKey, roleDto.getRoleKey())
                .and(w -> w.eq(PlatformRole::getIsDeleted, 0).or().isNull(PlatformRole::getIsDeleted))) > 0) {
            throw new BusinessException("角色标识已存在");
        }
        PlatformRole role = new PlatformRole();
        role.setRoleName(roleDto.getRoleName());
        role.setRoleKey(roleDto.getRoleKey());
        role.setDescription(roleDto.getDescription());
        role.setIsDeleted(0);
        platformRoleMapper.insert(role);
        saveRolePermissions(role.getId(), roleDto.getPermissionIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleDto roleDto) {
        PlatformRole role = platformRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if ("admin".equals(role.getRoleKey())) {
            throw new BusinessException("超级管理员角色不可修改");
        }
        if (!role.getRoleKey().equals(roleDto.getRoleKey())) {
            if (platformRoleMapper.selectCount(new LambdaQueryWrapper<PlatformRole>()
                    .eq(PlatformRole::getRoleKey, roleDto.getRoleKey())
                    .and(w -> w.eq(PlatformRole::getIsDeleted, 0).or().isNull(PlatformRole::getIsDeleted))) > 0) {
                throw new BusinessException("角色标识已存在");
            }
        }
        role.setRoleName(roleDto.getRoleName());
        role.setRoleKey(roleDto.getRoleKey());
        role.setDescription(roleDto.getDescription());
        platformRoleMapper.updateById(role);
        if (roleDto.getPermissionIds() != null) {
            platformRolePermissionMapper.delete(new LambdaQueryWrapper<PlatformRolePermission>()
                    .eq(PlatformRolePermission::getRoleId, id));
            saveRolePermissions(id, roleDto.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getRoleId, ids)) > 0) {
            throw new BusinessException("角色已关联用户，无法删除");
        }
        for (Long id : ids) {
            PlatformRole role = platformRoleMapper.selectById(id);
            if (role != null && "admin".equals(role.getRoleKey())) {
                throw new BusinessException("超级管理员角色不可删除");
            }
        }
        for (Long id : ids) {
            PlatformRole role = platformRoleMapper.selectById(id);
            if (role != null) {
                role.setIsDeleted(1);
                platformRoleMapper.updateById(role);
            }
        }
        platformRolePermissionMapper.delete(new LambdaQueryWrapper<PlatformRolePermission>()
                .in(PlatformRolePermission::getRoleId, ids));
    }

    private void saveRolePermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                PlatformRolePermission rp = new PlatformRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permissionId);
                platformRolePermissionMapper.insert(rp);
            }
        }
    }

    private PlatformRoleVo toVo(PlatformRole role) {
        PlatformRoleVo vo = new PlatformRoleVo();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    private void setUserCounts(List<PlatformRoleVo> roleVos) {
        try {
            List<Map<String, Object>> userCounts = platformRoleMapper.selectRoleUserCount();
            for (Map<String, Object> count : userCounts) {
                Long roleId = Long.valueOf(count.get("id").toString());
                Integer userCount = count.get("user_count") != null ? Integer.valueOf(count.get("user_count").toString()) : 0;
                for (PlatformRoleVo vo : roleVos) {
                    if (vo.getId().equals(roleId)) {
                        vo.setUserCount(userCount);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // table might not exist yet
        }
    }
}
