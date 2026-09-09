package com.xianshuyuan.scm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.system.entity.SystemUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUserEntity> {

    void lockSecurityWrites();

    long countUsableAdministratorsExcept(@Param("id") long id);

    SystemUserEntity lockActiveUser(@Param("id") long id);

    SystemUserEntity lockActiveUserByUsername(@Param("username") String username);

    int recordLoginFailure(@Param("id") long id, @Param("count") int count,
                           @Param("lockedUntil") java.time.OffsetDateTime lockedUntil,
                           @Param("incrementAuthVersion") boolean incrementAuthVersion);

    int recordLoginSuccess(@Param("id") long id, @Param("loginAt") java.time.OffsetDateTime loginAt);

    Long lockEnabledDepartment(@Param("id") long id);

    int updateProfile(@Param("user") SystemUserEntity user);

    int updateState(@Param("id") long id, @Param("version") int version,
                    @Param("status") String status, @Param("deleted") boolean deleted,
                    @Param("actor") String actor);

    int updatePassword(@Param("id") long id, @Param("version") int version,
                       @Param("passwordHash") String passwordHash, @Param("actor") String actor,
                       @Param("mustChangePassword") boolean mustChangePassword);

    void insertAudit(@Param("id") long id, @Param("actorId") Long actorId, @Param("actor") String actor,
                     @Param("operation") String operation,
                     @Param("before") String before, @Param("after") String after);

    SystemUserEntity selectActiveByUsername(@Param("username") String username);

    SystemUserEntity selectActiveSecurityStateById(@Param("id") long id);

    List<String> selectEnabledRoleCodes(@Param("userId") long userId);

    List<String> selectEnabledPermissionCodes(@Param("userId") long userId);
}
