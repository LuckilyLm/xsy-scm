package net.lab1024.sa.admin.module.scm.delivery.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.time.OffsetDateTime;

@Data
public abstract class DeliveryRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
