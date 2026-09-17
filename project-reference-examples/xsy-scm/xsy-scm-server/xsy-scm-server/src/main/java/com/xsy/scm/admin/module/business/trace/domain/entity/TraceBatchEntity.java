package com.xsy.scm.admin.module.business.trace.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 溯源批次（生产批号模式） 实体类
 *
 * <p>对标蔬东坡 17.5：生产批号维度与库存批次解耦，商品无需启用库存批次，
 * 也可通过生产批号关联供应商、生产日期、厂商与检测报告。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_trace_batch")
public class TraceBatchEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long batchId;

    /**
     * 批次号，PCB + yyyyMMdd + 4 位流水
     */
    private String batchNo;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 厂商 ID（关联 t_supplier_manufacturer）
     */
    private Long manufacturerId;

    /**
     * 生产批号（供应商 / 生产侧的实际批号）
     */
    private String produceBatchNo;

    /**
     * 产地
     */
    private String originPlace;

    /**
     * 生产 / 采收日期
     */
    private LocalDate produceDate;

    /**
     * 保质期单位：1 天，2 月
     */
    private Integer shelfLifeUnit;

    /**
     * 保质期数值
     */
    private Integer shelfLifeValue;

    /**
     * 到期日期（按自然日 / 自然月计算）
     */
    private LocalDate expireDate;

    /**
     * 状态：1 有效，2 已过期，3 已作废
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
