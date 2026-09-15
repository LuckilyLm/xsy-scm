package net.lab1024.sa.admin.module.scm.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户类型（可维护字典表）。
 *
 * <p>legacy 用可维护表、C 用前端硬编码枚举（1/2/3）。W2 采用 legacy 建模：枚举值下沉为
 * {@code customer_type} 的种子数据（ENTERPRISE / PERSONAL / GROUP），使新增类型无需发版。
 */
@Data
@TableName(value = "customer_type", autoResultMap = true)
public class CustomerTypeEntity {

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

    private String typeCode;

    private String name;

    private String status;
}
