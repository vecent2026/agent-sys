package com.trae.admin.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 自定义UserDetails实现，包含用户ID字段
 */
public class CustomUserDetails extends User {

    private final Long userId;

    public CustomUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities, Long userId) {
        super(username, password, authorities);
        this.userId = userId;
    }

    public CustomUserDetails(String username, String password, boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired, boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities, Long userId) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}