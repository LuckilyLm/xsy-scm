package com.xianshuyuan.scm.system.mapper;

import com.xianshuyuan.scm.system.entity.PermissionEntity;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionGrantMapper {
    List<PermissionEntity> permissions(@Param("roleId") long roleId);

    List<SystemUserEntity> lockActorAndAffectedUsers(@Param("roleId") long roleId, @Param("actorId") long actorId);

    void remove(@Param("roleId") long roleId, @Param("permissionId") long permissionId);

    void add(@Param("roleId") long roleId, @Param("permissionId") long permissionId, @Param("actor") String actor);

    int incrementVersion(@Param("roleId") long roleId, @Param("version") int version, @Param("actor") String actor);
}
