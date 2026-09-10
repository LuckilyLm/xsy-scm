package com.xianshuyuan.scm.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("sys_menu")
public class MenuEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String type;
    private String name;
    private String routeKey;
    private String path;
    private String iconKey;
    private String requiredPermissionCode;
    private Integer sortOrder;
    private Boolean visible;
    private String status;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
