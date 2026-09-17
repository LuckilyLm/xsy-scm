package com.xsy.scm.admin.module.business.trace.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检测报告 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_trace_inspect")
public class TraceInspectEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long inspectId;

    /**
     * 关联溯源批次 ID（绑定生产批号模式）
     */
    private Long batchId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 报告名称（对标蔬东坡 17.4）
     */
    private String reportName;

    /**
     * 匹配模式：1 绑定采购单，2 绑定生产批号
     */
    private Integer matchMode;

    /**
     * 报告图片文件（文件服务）
     */
    private String reportFile;

    /**
     * 报告 PDF 文件（文件服务）
     */
    private String pdfFile;

    /**
     * 检测日期
     */
    private LocalDate inspectDate;

    /**
     * 检测机构
     */
    private String inspectOrg;

    /**
     * 状态：1 有效，2 已作废
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
