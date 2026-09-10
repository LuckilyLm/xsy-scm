package com.xianshuyuan.scm.system.converter;

import com.xianshuyuan.scm.system.dto.DepartmentResponse;
import com.xianshuyuan.scm.system.dto.DepartmentTreeResponse;
import com.xianshuyuan.scm.system.entity.DepartmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DepartmentConverter {
    DepartmentResponse toResponse(DepartmentEntity entity);

    @Mapping(target = "children", ignore = true)
    DepartmentTreeResponse toTreeResponse(DepartmentEntity entity);
}
