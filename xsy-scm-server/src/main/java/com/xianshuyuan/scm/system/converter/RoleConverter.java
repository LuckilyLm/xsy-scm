package com.xianshuyuan.scm.system.converter;

import com.xianshuyuan.scm.system.dto.RoleResponse;
import com.xianshuyuan.scm.system.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleConverter {
    @Mapping(target = "roleCode", source = "code")
    RoleResponse toResponse(RoleEntity role);
}
