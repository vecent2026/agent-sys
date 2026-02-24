package com.trae.admin.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        List<SysPermission> permissions = sysPermissionMapper.selectPermissionsByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .filter(p -> StringUtils.hasText(p.getPermissionKey()))
                .map(p -> new SimpleGrantedAuthority(p.getPermissionKey()))
                .collect(Collectors.toList());

        return new CustomUserDetails(
                user.getUsername(),
                user.getPassword(),
                authorities,
                user.getId()
        );
    }
}
