package com.xianshuyuan.scm.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        long id,
        String username,
        String displayName,
        boolean administrator,
        boolean mustChangePassword,
        List<String> roles,
        List<String> permissions,
        int version
) {

    public CurrentUserResponse {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
