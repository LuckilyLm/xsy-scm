package com.xianshuyuan.scm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.system.entity.MenuEntity;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {
    MenuEntity lockActive(long id);
    long countRoute(@Param("key") String key, @Param("id") Long id);
    long countChildren(long id);
    long countLiveRoles(long id);
    long countPermission(String code);
    int updateMenu(@Param("menu") MenuEntity menu);
    List<SystemUserEntity> lockAffectedUsers(long id);
    void incrementAuthVersions(@Param("ids") List<Long> ids);
    void insertAudit(@Param("id") long id, @Param("actorId") long actorId, @Param("actor") String actor,
                     @Param("operation") String operation, @Param("before") String before, @Param("after") String after);
}
