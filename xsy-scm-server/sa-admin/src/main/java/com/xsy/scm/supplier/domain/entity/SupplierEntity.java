package com.xsy.scm.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 供应商（聚合根）。
 *
 * <p>
 * 可空联系人、电话和地址使用 {@code FieldStrategy.ALWAYS}，以便编辑时通过 {@code null} 清空字段；MyBatis-Plus 默认 {@code NOT_NULL} 会忽略这些更新。
 *
 * <p>
 * <b>点位</b>（{@code longitude} / {@code latitude} / {@code geomCrs}）成组存在：DB 的
 * {@code ck_supplier_location_complete} 与表单的 {@code ScmLocationForm} 都要求「三列同时有或同时无」，
 * 不允许保存半组坐标。坐标系必须随点保存 —— 没有 CRS 的经纬度在换底图时会静默偏移几百米。
 */
@Data
@TableName(value = "supplier", autoResultMap = true)
public class SupplierEntity {

    /** {@code null} 序列化为 JSON {@code null} 而不是省略：前端要靠「字段存在且为 null」区分「未采集」。 */
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal longitude;

    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal latitude;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String geomCrs;

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

    private String supplierCode;

    private String name;

    private String status;

    private Integer paymentPeriodDays;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPhone;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;

    /**
     * 省 / 市 / 区编码为国标六位码，名称是同一条选择的快照，展示与导出用，不参与关联。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer provinceCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String provinceName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cityCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cityName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer districtCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String districtName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
