package com.xianshuyuan.scm.system.converter;
import com.xianshuyuan.scm.system.dto.UserResponse;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import org.mapstruct.Mapper;
@Mapper(componentModel="spring", unmappedTargetPolicy=org.mapstruct.ReportingPolicy.ERROR)
public interface UserConverter {
    UserResponse toResponse(SystemUserEntity entity);
}
