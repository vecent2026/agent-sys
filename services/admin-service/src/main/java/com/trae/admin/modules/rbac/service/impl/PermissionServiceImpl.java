package com.trae.admin.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.rbac.dto.PermissionDto;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.entity.SysRolePermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.rbac.mapper.SysRolePermissionMapper;
import com.trae.admin.modules.rbac.service.PermissionService;
import com.trae.admin.modules.rbac.vo.PermissionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    public List<PermissionVo> listTree() {
        List<SysPermission> all = sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSort));
        
        List<PermissionVo> voList = all.stream().map(this::convertToVo).collect(Collectors.toList());
        
        // Build tree
        Map<Long, List<PermissionVo>> parentMap = voList.stream()
                .collect(Collectors.groupingBy(PermissionVo::getParentId));
        
        List<PermissionVo> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        buildChildren(roots, parentMap);
        
        return roots;
    }

    @Override
    public List<PermissionVo> listAll(String name) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like(SysPermission::getName, name);
        }
        List<SysPermission> all = sysPermissionMapper.selectList(wrapper.orderByAsc(SysPermission::getSort));
        return all.stream().map(this::convertToVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(PermissionDto permissionDto) {
        // Check permission key uniqueness
        if (permissionDto.getPermissionKey() != null) {
            if (sysPermissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getPermissionKey, permissionDto.getPermissionKey())) > 0) {
                throw new BusinessException("权限标识已存在");
            }
        }

        SysPermission permission = new SysPermission();
        BeanUtils.copyProperties(permissionDto, permission);
        sysPermissionMapper.insert(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PermissionDto permissionDto) {
        SysPermission permission = sysPermissionMapper.selectById(permissionDto.getId());
        if (permission == null) {
            throw new BusinessException("Permission not found");
        }

        // Check permission key uniqueness if changed
        if (!java.util.Objects.equals(permission.getPermissionKey(), permissionDto.getPermissionKey())) {
            if (permissionDto.getPermissionKey() != null && sysPermissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getPermissionKey, permissionDto.getPermissionKey())) > 0) {
                throw new BusinessException("权限标识已存在");
            }
        }

        BeanUtils.copyProperties(permissionDto, permission);
        sysPermissionMapper.updateById(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // Check children
        if (sysPermissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, id)) > 0) {
            throw new BusinessException("Cannot delete permission with children");
        }
        
        sysPermissionMapper.deleteById(id);
        
        // Delete role relations
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getPermissionId, id));
    }

    private void buildChildren(List<PermissionVo> nodes, Map<Long, List<PermissionVo>> parentMap) {
        for (PermissionVo node : nodes) {
            List<PermissionVo> children = parentMap.get(node.getId());
            if (children != null) {
                node.setChildren(children);
                buildChildren(children, parentMap);
            }
        }
    }

    private PermissionVo convertToVo(SysPermission permission) {
        PermissionVo vo = new PermissionVo();
        BeanUtils.copyProperties(permission, vo);
        // 日志记录默认开启
        vo.setLogEnabled(true);
        return vo;
    }
}
