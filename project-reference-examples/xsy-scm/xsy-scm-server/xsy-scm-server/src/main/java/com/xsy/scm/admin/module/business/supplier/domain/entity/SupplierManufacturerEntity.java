package com.xsy.scm.admin.module.business.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商厂商信息 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_supplier_manufacturer")
public class SupplierManufacturerEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long manufacturerId;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 厂商名称
     */
    private String manufacturerName;

    /**
     * 资质证件（文件服务）
     */
    private String qualificationFile;

    /**
     * 资质到期日期（支持预警）
     */
    private LocalDate qualificationExpireDate;

    /**
     * 质检报告文件（文件服务）
     */
    private String inspectReportFile;

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
