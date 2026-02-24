package com.trae.admin.modules.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.modules.user.dto.UserDto;
import com.trae.admin.modules.user.dto.UserQueryDto;
import com.trae.admin.modules.user.vo.UserVo;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 分页查询用户
     */
    Page<UserVo> page(UserQueryDto queryDto);

    /**
     * 获取用户详情
     */
    UserVo get(Long id);

    /**
     * 新增用户
     */
    void save(UserDto userDto);

    /**
     * 修改用户
     */
    void update(UserDto userDto);

    /**
     * 删除用户
     */
    void delete(List<Long> ids);

    /**
     * 修改密码
     */
    void changePassword(Long id, String oldPassword, String newPassword);

    /**
     * 重置密码
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 修改用户状态
     */
    void changeStatus(Long id, Integer status);
    
    /**
     * 获取用户角色ID列表
     */
    List<Long> getUserRoleIds(Long userId);
    
    /**
     * 分配用户角色
     */
    void assignUserRoles(Long userId, List<Long> roleIds);
}
