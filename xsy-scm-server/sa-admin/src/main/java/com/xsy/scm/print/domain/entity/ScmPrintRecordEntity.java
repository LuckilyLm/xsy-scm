package com.xsy.scm.print.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 正式打印的冻结记录。
 *
 * <p>
 * 表上有触发器拒绝 UPDATE / DELETE：重印必须与当初那张逐字一致，能改就等于没冻结。
 * 因此这里没有 {@code deleted} / {@code version}，也没有任何「更新记录」的写法。
 *
 * <p>
 * {@code modelSnapshot} 是打印当时的模板模型，{@code dataSnapshot} 是当时的渲染结果
 * （表头字段、列定义、行、合计、被权限剔除的字段）。重印只读这两份快照，不回业务表重算。
 */
@Data
@TableName(value = "scm_print_record", autoResultMap = true)
public class ScmPrintRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String documentType;

    private Long businessId;

    private String businessNo;

    private Long templateId;

    private String templateCode;

    private String templateName;

    private Integer templateVersion;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> modelSnapshot;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> dataSnapshot;

    private OffsetDateTime printedAt;

    private String printedBy;
}
