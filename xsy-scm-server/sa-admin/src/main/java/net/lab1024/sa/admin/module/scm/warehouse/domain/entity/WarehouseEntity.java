package net.lab1024.sa.admin.module.scm.warehouse.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.time.OffsetDateTime;

/**
 * 仓库（最小主数据，W5 Target Design Q1 / §5.2）。
 *
 * <p>W5 只维护**一个**启用仓库（G-03 单仓库口径，由种子数据表达），
 * 但**不在 DB 层**加「最多一行」约束 —— 那会让 W6 / 未来多仓扩展必须改约束。
 */
@Data @TableName(value="warehouse",autoResultMap=true)
public class WarehouseEntity {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String warehouseCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String name;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String address;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark;
    @Version private Integer version=0;
    @TableLogic(value="false",delval="true") private Boolean deleted=false;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime updatedAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String updatedBy;
}
