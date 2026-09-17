package com.xsy.scm.admin.module.business.external.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部平台映射 实体类
 *
 * <p>对标蔬东坡 17.3 / 17.4：商品 / 客户 / 供应商与外部平台的 ID 映射，
 * 支持一对多与单位转换系数（农批市场 1 个市场商品最多绑定 10 个系统商品）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_external_mapping")
public class ExternalMappingEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long mappingId;

    /**
     * 平台类型，见 finance 模块 ExternalSystemTypeEnum
     */
    private Integer systemType;

    /**
     * 映射对象：1 商品，2 客户，3 供应商
     */
    private Integer bizType;

    /**
     * 系统内 ID
     */
    private Long localId;

    /**
     * 外部平台 ID
     */
    private String externalId;

    /**
     * 单位转换系数（未填默认 1）
     */
    private BigDecimal convertRatio;

    /**
     * 状态：1 启用，2 停用
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
