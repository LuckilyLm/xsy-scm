package com.xsy.scm.admin.module.business.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计凭证 实体类
 *
 * <p>由应收应付 / 收付款 / 费用单生成，推送外部财务软件；借贷必须平衡。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_finance_voucher")
public class FinanceVoucherEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long voucherId;

    /**
     * 凭证号，PZD + yyyyMMdd + 4 位流水
     */
    private String voucherNo;

    /**
     * 凭证日期
     */
    private LocalDate voucherDate;

    /**
     * 凭证类型：1 收款，2 付款，3 应收，4 应付，5 费用
     */
    private Integer voucherType;

    /**
     * 关联业务类型（见 StockBizTypeEnum / 各模块 bizType 约定）
     */
    private Integer bizType;

    /**
     * 关联业务单 ID
     */
    private Long bizId;

    /**
     * 借方合计（不含税）
     */
    private BigDecimal totalDebit;

    /**
     * 贷方合计（不含税）
     */
    private BigDecimal totalCredit;

    /**
     * 同步状态：1 未同步，2 同步中，3 同步成功，4 同步失败
     */
    private Integer syncStatus;

    /**
     * 外部系统单据号（同步成功后回写）
     */
    private String externalNo;

    /**
     * 凭证状态：1 已生成，2 已作废
     */
    private Integer status;

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
     * 删除状态：0 否，1 是（凭证不允许物理删除）
     */
    private Boolean deletedFlag;
}
