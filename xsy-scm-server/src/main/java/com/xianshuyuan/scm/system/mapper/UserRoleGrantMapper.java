package com.xianshuyuan.scm.system.mapper;

import com.xianshuyuan.scm.system.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRoleGrantMapper {
    List<RoleEntity> roles(@Param("userId") long userId);

    List<String> permissions(@Param("roleIds") List<Long> roleIds);

    List<Long> menus(@Param("roleIds") List<Long> roleIds);

    void remove(@Param("userId") long userId, @Param("roleId") long roleId);

    void add(@Param("userId") long userId, @Param("roleId") long roleId, @Param("actor") String actor);

    int incrementVersion(@Param("id") long id, @Param("version") int version, @Param("actor") String actor);
}
