package com.xianshuyuan.scm.auth.service;

import com.xianshuyuan.scm.auth.dto.CurrentUserResponse;
import com.xianshuyuan.scm.auth.security.AuthenticatedUser;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AuthIdentityService {

    private final SystemUserMapper userMapper;

    public AuthIdentityService(SystemUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public AuthenticatedUser loadPrincipal(String username) {
        SystemUserEntity user = userMapper.selectActiveByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User no longer exists");
        }
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAuthVersion(),
                Boolean.TRUE.equals(user.getAdministrator()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                userMapper.selectEnabledRoleCodes(user.getId())
        );
    }

    public List<SimpleGrantedAuthority> loadAuthorities(AuthenticatedUser user) {
        List<String> permissionCodes = userMapper.selectEnabledPermissionCodes(user.userId());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(permissionCodes.size() + 1);
        permissionCodes.stream().filter(code -> !"system.administrator".equals(code))
                .map(SimpleGrantedAuthority::new).forEach(authorities::add);
        if (user.administrator()) {
            authorities.add(new SimpleGrantedAuthority("system.administrator"));
        }
        return List.copyOf(authorities);
    }

    public CurrentUserResponse toResponse(Authentication authentication) {
        AuthenticatedUser user = principal(authentication);
        SystemUserEntity current = userMapper.selectById(user.userId());
        if (current == null || !java.util.Objects.equals(current.getAuthVersion(), user.authVersion())) {
            throw new com.xianshuyuan.scm.common.exception.BusinessException(
                    com.xianshuyuan.scm.auth.AuthErrorCodes.SESSION_INVALID);
        }
        List<String> permissions = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(code -> user.administrator() || !"system.administrator".equals(code))
                .sorted()
                .toList();
        return new CurrentUserResponse(
                user.userId(),
                user.username(),
                user.displayName(),
                user.administrator(),
                user.mustChangePassword(),
                user.roleCodes(),
                permissions,
                current.getVersion()
        );
    }

    private AuthenticatedUser principal(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        if (principal instanceof com.xianshuyuan.scm.auth.security.SystemUserDetails userDetails) {
            return userDetails.getUser();
        }
        throw new IllegalStateException("Unsupported authenticated principal");
    }
}
