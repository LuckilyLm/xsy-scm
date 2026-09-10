package com.xianshuyuan.scm.system.dto;

import java.util.List;

public record UserRolesResponse(long userId, int version, List<Role> roles) {
    public record Role(long id, String code, String name, String status) {
    }
}
