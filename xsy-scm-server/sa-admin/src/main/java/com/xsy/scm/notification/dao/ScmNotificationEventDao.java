package com.xsy.scm.notification.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScmNotificationEventDao {
    int insertIgnore(@Param("eventKey") String eventKey, @Param("eventType") String eventType,
            @Param("receiverUserType") Integer receiverUserType, @Param("receiverUserId") Long receiverUserId,
            @Param("dataId") Long dataId, @Param("createdBy") String createdBy);
}
