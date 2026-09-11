package com.xianshuyuan.scm.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/**
 * 首页板块配置。抢购、新品推荐、分类等版块按 sort_order 依次渲染，支持定时上下线。
 */
@Data
@TableName(value = "marketing_home_section", autoResultMap = true)
public class MarketingHomeSectionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sectionType;
    private String title;
    private Long promotionId;
    private Long categoryId;
    private Integer sortOrder;
    private String status;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    @TableField(typeHandler = JsonbJsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode payload;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
