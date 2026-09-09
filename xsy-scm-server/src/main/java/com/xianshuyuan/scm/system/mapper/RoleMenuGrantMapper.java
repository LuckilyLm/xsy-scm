package com.xianshuyuan.scm.system.mapper;

import com.xianshuyuan.scm.system.entity.MenuEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface RoleMenuGrantMapper {
    List<MenuEntity> menus(@Param("roleId") long roleId);
    List<Long> actorMenuIds(@Param("userId") long userId);
    java.util.List<com.xianshuyuan.scm.system.entity.SystemUserEntity> lockActorAndAffectedUsers(@Param("roleId") long roleId, @Param("actorId") long actorId);
    void remove(@Param("roleId") long roleId, @Param("menuId") long menuId);
    void add(@Param("roleId") long roleId, @Param("menuId") long menuId, @Param("actor") String actor);
    int incrementVersion(@Param("roleId") long roleId, @Param("version") int version, @Param("actor") String actor);
}
