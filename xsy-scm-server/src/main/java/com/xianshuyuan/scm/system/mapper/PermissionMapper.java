package com.xianshuyuan.scm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.system.entity.PermissionEntity;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {
    PermissionEntity lockActive(@Param("id") long id);

    long countCode(@Param("code") String code);

    long countMenuReferences(@Param("code") String code);

    long countRetainedRoleGrants(@Param("permissionId") long permissionId);

    int updatePermission(@Param("permission") PermissionEntity permission);

    List<SystemUserEntity> lockAffectedUsers(@Param("permissionId") long permissionId);

    int incrementAuthVersions(@Param("permissionId") long permissionId);

    void insertAudit(@Param("id") long id, @Param("actorId") Long actorId, @Param("actor") String actor,
                     @Param("operation") String operation, @Param("before") String before, @Param("after") String after);
}
