package com.xsy.scm.admin.module.business.trace.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 溯源码 实体类
 *
 * <p>颗粒度已定：按批次（一码一批，13-01 / G-08）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_trace_code")
public class TraceCodeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long codeId;

    /**
     * 唯一溯源码
     */
    private String traceCode;

    /**
     * 类型：1 批次码
     */
    private Integer codeType;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 关联溯源批次 ID
     */
    private Long batchId;

    /**
     * 二维码图片（文件服务）
     */
    private String qrcodeUrl;

    /**
     * 状态：1 未启用，2 已启用，3 已作废
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
