package com.xianshuyuan.scm.system.converter;

import com.xianshuyuan.scm.system.dto.PermissionResponse;
import com.xianshuyuan.scm.system.entity.PermissionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PermissionConverter {
    @Mapping(target = "permissionCode", source = "code")
    @Mapping(target = "type", source = "resourceType")
    PermissionResponse toResponse(PermissionEntity permission);
}
