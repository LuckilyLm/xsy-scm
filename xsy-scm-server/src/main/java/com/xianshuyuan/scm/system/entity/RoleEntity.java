package com.xianshuyuan.scm.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("sys_role")
public class RoleEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private Boolean systemRole;
    @Version private Integer version;
    @TableLogic private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
