package com.xianshuyuan.scm.auth.security;

import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemUserDetailsService implements UserDetailsService {

    private static final String ADMINISTRATOR_AUTHORITY = "system.administrator";

    private final SystemUserMapper userMapper;

    public SystemUserDetailsService(SystemUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalizedUsername = username == null ? "" : username.trim();
        SystemUserEntity user = userMapper.selectActiveByUsername(normalizedUsername);
        if (user == null || user.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        List<String> roleCodes = userMapper.selectEnabledRoleCodes(user.getId());
        List<String> permissionCodes = userMapper.selectEnabledPermissionCodes(user.getId());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(permissionCodes.size() + 1);
        permissionCodes.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
        if (Boolean.TRUE.equals(user.getAdministrator())) {
            authorities.add(new SimpleGrantedAuthority(ADMINISTRATOR_AUTHORITY));
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAuthVersion(),
                Boolean.TRUE.equals(user.getAdministrator()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                roleCodes
        );

        boolean enabled = "ENABLED".equals(user.getStatus());
        boolean accountNonLocked = user.getLockedUntil() == null
                || !user.getLockedUntil().isAfter(OffsetDateTime.now());
        return new SystemUserDetails(
                principal,
                user.getPasswordHash(),
                authorities,
                enabled,
                accountNonLocked
        );
    }
}
