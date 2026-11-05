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
 * 正式打印记录只能新增和读取，不能修改或删除。历史重印使用当时的模板和版面内容，不按当前业务数据重新计算。
 */
@Data
@TableName(value = "xsy_print_record", autoResultMap = true)
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
