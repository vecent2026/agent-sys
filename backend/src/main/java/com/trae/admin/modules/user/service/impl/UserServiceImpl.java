package com.trae.admin.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.user.dto.UserDto;
import com.trae.admin.modules.user.dto.UserQueryDto;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.entity.SysUserRole;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
import com.trae.admin.modules.user.service.UserService;
import com.trae.admin.modules.user.vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    @Override
    public Page<UserVo> page(UserQueryDto queryDto) {
        Page<SysUser> page = new Page<>(queryDto.getPage(), queryDto.getSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        
        // 添加逻辑删除条件
        wrapper.eq(SysUser::getIsDeleted, 0);
        
        if (StringUtils.hasText(queryDto.getUsername())) {
            wrapper.like(SysUser::getUsername, queryDto.getUsername());
        }
        if (StringUtils.hasText(queryDto.getMobile())) {
            wrapper.like(SysUser::getMobile, queryDto.getMobile());
        }
        if (queryDto.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, queryDto.getStatus());
        }
        
        wrapper.orderByDesc(SysUser::getCreateTime);
        
        Page<SysUser> userPage = sysUserMapper.selectPage(page, wrapper);
        
        Page<UserVo> resultPage = new Page<>();
        BeanUtils.copyProperties(userPage, resultPage);
        
        List<UserVo> list = userPage.getRecords().stream().map(this::convertToVo).collect(Collectors.toList());
        resultPage.setRecords(list);
        
        return resultPage;
    }

    @Override
    public UserVo get(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        return convertToVo(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UserDto userDto) {
        // Check duplicate username
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, userDto.getUsername())) > 0) {
            throw new BusinessException("Username already exists");
        }

        SysUser user = new SysUser();
        BeanUtils.copyProperties(userDto, user);
        
        // Set default password if not provided
        String rawPassword = StringUtils.hasText(userDto.getPassword()) ? userDto.getPassword() : "123456";
        user.setPassword(passwordEncoder.encode(rawPassword));
        
        sysUserMapper.insert(user);
        
        // Save roles
        saveUserRoles(user.getId(), userDto.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserDto userDto) {
        SysUser user = sysUserMapper.selectById(userDto.getId());
        if (user == null) {
            throw new BusinessException("User not found");
        }

        // Check duplicate username if changed
        if (!user.getUsername().equals(userDto.getUsername())) {
             if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, userDto.getUsername())) > 0) {
                throw new BusinessException("Username already exists");
            }
        }

        // Check if status changed from enabled to disabled
        if (user.getStatus() == 1 && userDto.getStatus() == 0) {
            // Force logout by incrementing token version
            String versionKey = "auth:version:" + user.getUsername();
            redisUtil.increment(versionKey);
        }

        BeanUtils.copyProperties(userDto, user, "password"); // Don't update password here
        sysUserMapper.updateById(user);

        // Update roles
        if (userDto.getRoleIds() != null) {
            // Delete old roles
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, user.getId()));
            // Save new roles
            saveUserRoles(user.getId(), userDto.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        // 逻辑删除用户
        sysUserMapper.deleteBatchIds(ids);
        // 删除用户角色关联
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, ids));
    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("Old password incorrect");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }

        // Force logout by incrementing token version
        String versionKey = "auth:version:" + user.getUsername();
        redisUtil.increment(versionKey);

        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }

        // Check if status changed from enabled to disabled
        if (user.getStatus() == 1 && status == 0) {
            // Force logout by incrementing token version
            String versionKey = "auth:version:" + user.getUsername();
            redisUtil.increment(versionKey);
        }

        user.setStatus(status);
        sysUserMapper.updateById(user);
    }
    
    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return sysUserRoleMapper.selectRoleIdsByUserId(userId);
    }
    
    @Override
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        // 先删除用户原有角色
        sysUserRoleMapper.deleteByUserId(userId);
        
        // 分配新角色
        saveUserRoles(userId, roleIds);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                sysUserRoleMapper.insert(userRole);
            }
        }
    }

    private UserVo convertToVo(SysUser user) {
        UserVo vo = new UserVo();
        BeanUtils.copyProperties(user, vo);
        List<String> roleNames = sysUserMapper.selectRoleNamesByUserId(user.getId());
        vo.setRoleNames(roleNames);
        List<Long> roleIds = sysUserMapper.selectRoleIdsByUserId(user.getId());
        vo.setRoleIds(roleIds);
        return vo;
    }
}
