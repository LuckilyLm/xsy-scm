package com.xianshuyuan.scm.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 商城跨端主题配置，业务上只保留一条当前配置。
 */
@Data
@TableName("marketing_theme_config")
public class MarketingThemeConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String themeCode;
    private String primaryColor;
    private String accentColor;
    private String pageBackground;
    private Integer cardRadius;
    private String cardStyle;
    private String productCardStyle;
    private String navigationStyle;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
