package com.trae.admin.modules.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.rbac.vo.PermissionVo;
import com.trae.admin.modules.rbac.vo.RoleVo;
import com.trae.admin.modules.tenant.entity.TenantRole;
import com.trae.admin.modules.tenant.entity.TenantRolePermission;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import com.trae.admin.modules.tenant.mapper.TenantPermissionMapper;
import com.trae.admin.modules.tenant.mapper.TenantRoleMapper;
import com.trae.admin.modules.tenant.mapper.TenantRolePermissionMapper;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import com.trae.admin.modules.tenant.service.TenantRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantRoleServiceImpl implements TenantRoleService {

    private final TenantRoleMapper tenantRoleMapper;
    private final TenantRolePermissionMapper tenantRolePermissionMapper;
    private final TenantUserRoleMapper tenantUserRoleMapper;
    private final TenantPermissionMapper tenantPermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public Page<RoleVo> page(RoleQueryDto queryDto) {
        Long tenantId = requireTenantId();
        Page<TenantRole> page = new Page<>(queryDto.getPage(), queryDto.getSize());
        LambdaQueryWrapper<TenantRole> wrapper = new LambdaQueryWrapper<TenantRole>()
                .eq(TenantRole::getTenantId, tenantId)
                .eq(TenantRole::getIsDeleted, 0)
                .orderByDesc(TenantRole::getCreateTime);
        if (StringUtils.hasText(queryDto.getRoleName())) {
            wrapper.like(TenantRole::getRoleName, queryDto.getRoleName());
        }
        if (StringUtils.hasText(queryDto.getRoleKey())) {
            wrapper.like(TenantRole::getRoleKey, queryDto.getRoleKey());
        }

        Page<TenantRole> rolePage = tenantRoleMapper.selectPage(page, wrapper);
        Page<RoleVo> result = new Page<>();
        BeanUtils.copyProperties(rolePage, result);
        List<RoleVo> records = rolePage.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        setUserCounts(tenantId, records);
        result.setRecords(records);
        return result;
    }

    @Override
    public List<RoleVo> listAll() {
        Long tenantId = requireTenantId();
        List<RoleVo> records = tenantRoleMapper.selectList(new LambdaQueryWrapper<TenantRole>()
                        .eq(TenantRole::getTenantId, tenantId)
                        .eq(TenantRole::getIsDeleted, 0)
                        .orderByDesc(TenantRole::getCreateTime))
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
        setUserCounts(tenantId, records);
        return records;
    }

    @Override
    public RoleVo get(Long id) {
        return toVo(requireRole(id));
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        Long tenantId = requireTenantId();
        requireRole(roleId);
        return tenantRolePermissionMapper.selectPermissionIdsByRoleId(roleId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Long tenantId = requireTenantId();
        TenantRole role = requireRole(roleId);
        if (Integer.valueOf(1).equals(role.getIsBuiltin())) {
            throw new BusinessException("内置超管角色权限不可修改");
        }
        validatePermissionScope(tenantId, permissionIds);
        tenantRolePermissionMapper.deleteByRoleId(roleId, tenantId);
        saveRolePermissions(tenantId, roleId, permissionIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(RoleDto roleDto) {
        Long tenantId = requireTenantId();
        ensureRoleKeyUnique(tenantId, roleDto.getRoleKey(), null);
        TenantRole role = new TenantRole();
        role.setTenantId(tenantId);
        role.setRoleName(roleDto.getRoleName());
        role.setRoleKey(roleDto.getRoleKey());
        role.setDescription(roleDto.getDescription());
        role.setIsBuiltin(0);
        role.setIsSuper(0);
        role.setIsDeleted(0);
        tenantRoleMapper.insert(role);
        saveRolePermissions(tenantId, role.getId(), roleDto.getPermissionIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleDto roleDto) {
        Long tenantId = requireTenantId();
        TenantRole role = requireRole(id);
        if (Integer.valueOf(1).equals(role.getIsBuiltin())) {
            throw new BusinessException("内置超管角色不可修改");
        }
        ensureRoleKeyUnique(tenantId, roleDto.getRoleKey(), id);
        role.setRoleName(roleDto.getRoleName());
        role.setRoleKey(roleDto.getRoleKey());
        role.setDescription(roleDto.getDescription());
        tenantRoleMapper.updateById(role);
        if (roleDto.getPermissionIds() != null) {
            validatePermissionScope(tenantId, roleDto.getPermissionIds());
            tenantRolePermissionMapper.deleteByRoleId(id, tenantId);
            saveRolePermissions(tenantId, id, roleDto.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        Long tenantId = requireTenantId();
        List<TenantRole> roles = tenantRoleMapper.selectBatchIds(ids).stream()
                .filter(role -> role != null && tenantId.equals(role.getTenantId()) && !Integer.valueOf(1).equals(role.getIsDeleted()))
                .collect(Collectors.toList());
        if (roles.size() != ids.size()) {
            throw new BusinessException("角色不存在");
        }
        for (TenantRole role : roles) {
            if (Integer.valueOf(1).equals(role.getIsBuiltin())) {
                throw new BusinessException("内置超管角色不可删除");
            }
        }
        long boundCount = tenantUserRoleMapper.selectCount(new LambdaQueryWrapper<TenantUserRole>()
                .eq(TenantUserRole::getTenantId, tenantId)
                .in(TenantUserRole::getRoleId, ids));
        if (boundCount > 0) {
            throw new BusinessException("角色已关联成员，无法删除");
        }
        for (TenantRole role : roles) {
            role.setIsDeleted(1);
            tenantRoleMapper.updateById(role);
        }
        for (Long id : ids) {
            tenantRolePermissionMapper.deleteByRoleId(id, tenantId);
        }
    }

    @Override
    public List<PermissionVo> listAvailablePermissions() {
        Long tenantId = requireTenantId();
        List<Long> permissionIds = tenantPermissionMapper.selectPermissionIdsByTenantId(tenantId);
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysPermission> permissions = sysPermissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> permission != null
                        && !Integer.valueOf(1).equals(permission.getIsDeleted())
                        && "tenant".equals(permission.getScope()))
                .sorted(java.util.Comparator.comparing(SysPermission::getSort, java.util.Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
        List<PermissionVo> voList = permissions.stream().map(this::toPermissionVo).collect(Collectors.toList());
        Map<Long, List<PermissionVo>> parentMap = voList.stream()
                .collect(Collectors.groupingBy(vo -> vo.getParentId() == null ? 0L : vo.getParentId()));
        List<PermissionVo> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        buildPermissionChildren(roots, parentMap);
        return roots;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("租户上下文缺失");
        }
        return tenantId;
    }

    private TenantRole requireRole(Long id) {
        Long tenantId = requireTenantId();
        TenantRole role = tenantRoleMapper.selectById(id);
        if (role == null || !tenantId.equals(role.getTenantId()) || Integer.valueOf(1).equals(role.getIsDeleted())) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private void ensureRoleKeyUnique(Long tenantId, String roleKey, Long excludeId) {
        LambdaQueryWrapper<TenantRole> wrapper = new LambdaQueryWrapper<TenantRole>()
                .eq(TenantRole::getTenantId, tenantId)
                .eq(TenantRole::getRoleKey, roleKey)
                .eq(TenantRole::getIsDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(TenantRole::getId, excludeId);
        }
        if (tenantRoleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("角色标识已存在");
        }
    }

    private void validatePermissionScope(Long tenantId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        Set<Long> allowed = new HashSet<>(tenantPermissionMapper.selectPermissionIdsByTenantId(tenantId));
        if (!allowed.containsAll(permissionIds)) {
            throw new BusinessException("包含未授权的权限节点");
        }
    }

    private void saveRolePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        validatePermissionScope(tenantId, permissionIds);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (Long permissionId : permissionIds) {
            TenantRolePermission rp = new TenantRolePermission();
            rp.setTenantId(tenantId);
            rp.setRoleId(roleId);
            rp.setPermissionId(permissionId);
            tenantRolePermissionMapper.insert(rp);
        }
    }

    private RoleVo toVo(TenantRole role) {
        RoleVo vo = new RoleVo();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    private void setUserCounts(Long tenantId, List<RoleVo> roleVos) {
        List<Map<String, Object>> counts = tenantRoleMapper.selectRoleUserCount(tenantId);
        for (Map<String, Object> count : counts) {
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

    private PermissionVo toPermissionVo(SysPermission permission) {
        PermissionVo vo = new PermissionVo();
        BeanUtils.copyProperties(permission, vo);
        vo.setLogEnabled(true);
        return vo;
    }

    private void buildPermissionChildren(List<PermissionVo> nodes, Map<Long, List<PermissionVo>> parentMap) {
        for (PermissionVo node : nodes) {
            List<PermissionVo> children = parentMap.get(node.getId());
            if (children != null) {
                node.setChildren(children);
                buildPermissionChildren(children, parentMap);
            }
        }
    }
}
