package com.xianshuyuan.scm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.system.entity.RoleEntity;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
    RoleEntity lockActive(@Param("id") long id);

    long countCode(@Param("code") String code, @Param("excludedId") Long excludedId);

    int updateRole(@Param("role") RoleEntity role);

    List<SystemUserEntity> lockAffectedUsers(@Param("roleId") long roleId);

    int incrementAuthVersions(@Param("roleId") long roleId);

    List<String> activatablePermissions(@Param("roleId") long roleId);

    List<String> permissionsLost(@Param("roleId") long roleId, @Param("userId") long userId);

    void insertAudit(@Param("id") long id, @Param("actorId") Long actorId, @Param("actor") String actor,
                     @Param("operation") String operation, @Param("before") String before, @Param("after") String after);
}
