package com.xsy.scm.admin.module.business.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 询价单 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_inquiry")
public class InquiryEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long inquiryId;

    /**
     * 询价单号，XJD + yyyyMMdd + 4 位流水
     */
    private String inquiryNo;

    /**
     * 询价单名称
     */
    private String inquiryName;

    /**
     * 询价有效开始时间
     */
    private LocalDateTime validStart;

    /**
     * 询价有效结束时间
     */
    private LocalDateTime validEnd;

    /**
     * 状态：1 待报价，2 报价中，3 已完成，4 已取消
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
