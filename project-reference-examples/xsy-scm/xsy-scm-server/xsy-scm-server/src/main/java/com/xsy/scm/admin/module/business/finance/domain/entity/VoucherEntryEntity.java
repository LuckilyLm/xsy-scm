package com.xsy.scm.admin.module.business.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 凭证分录 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_voucher_entry")
public class VoucherEntryEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long entryId;

    /**
     * 凭证 ID
     */
    private Long voucherId;

    /**
     * 会计科目编码
     */
    private String subjectCode;

    /**
     * 会计科目名称
     */
    private String subjectName;

    /**
     * 借贷方向：1 借，2 贷
     */
    private Integer direction;

    /**
     * 金额（不含税）
     */
    private BigDecimal amount;

    /**
     * 创建人ID
     */
    private Long createUserId;

    /**
     * 创建人姓名
     */
    private String createUserName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 删除状态：0 否，1 是
     */
    private Boolean deletedFlag;
}
