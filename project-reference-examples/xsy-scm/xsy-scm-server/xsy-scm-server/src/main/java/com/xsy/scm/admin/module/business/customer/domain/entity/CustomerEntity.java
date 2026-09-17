package com.xsy.scm.admin.module.business.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户档案 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_customer")
public class CustomerEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long customerId;

    /**
     * 客户编码，KH + 6 位流水
     */
    private String customerNo;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户类型：1 企业，2 个人，3 集团
     */
    private Integer customerType;

    /**
     * 客户分级 ID，影响取价
     */
    private Long customerLevelId;

    /**
     * 上级集团客户 ID，独立客户为 0
     */
    private Long parentCustomerId;

    /**
     * 结算方式：1 独立结算，2 集团统一结算
     */
    private Integer settleMode;

    /**
     * 归属业务员 ID
     */
    private Long sellerId;

    /**
     * 绑定供应商 ID
     */
    private Long supplierId;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 地址，配送使用
     */
    private String address;

    /**
     * 经度，线路规划使用
     */
    private BigDecimal longitude;

    /**
     * 纬度，线路规划使用
     */
    private BigDecimal latitude;

    /**
     * 余额账户（不含税），余额充值结算使用
     */
    private BigDecimal balance;

    /**
     * 授信额度（不含税），账期按金额时使用
     */
    private BigDecimal creditAmount;

    /**
     * 状态：1 潜在，2 合作中，3 暂停合作，4 黑名单
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
     * 删除状态：0 否，1 是
     */
    private Boolean deletedFlag;
}
