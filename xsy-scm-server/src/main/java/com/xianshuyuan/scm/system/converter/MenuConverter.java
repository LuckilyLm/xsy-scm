package com.xianshuyuan.scm.system.converter;

import com.xianshuyuan.scm.system.dto.MenuResponse;
import com.xianshuyuan.scm.system.entity.MenuEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MenuConverter {
    @Mapping(target = "icon", source = "iconKey")
    @Mapping(target = "requiredPermission", source = "requiredPermissionCode")
    @Mapping(target = "sort", source = "sortOrder")
    @Mapping(target = "withChildren", ignore = true)
    @Mapping(target = "children", expression = "java(java.util.List.of())")
    MenuResponse toResponse(MenuEntity menu);
}
