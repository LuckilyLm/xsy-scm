package com.xsy.scm.admin.module.business.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部系统配置 实体类
 *
 * <p>密钥（appSecret）加密存储，禁止入库明文 / 提交 Git。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_external_config")
public class ExternalConfigEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long configId;

    /**
     * 系统类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8，5 溯源平台，6 团餐平台
     */
    private Integer systemType;

    /**
     * 接口地址
     */
    private String apiUrl;

    /**
     * 应用 Key
     */
    private String appKey;

    /**
     * 应用密钥（加密存储，禁止明文）
     */
    private String appSecret;

    /**
     * 字段映射配置（JSON）
     */
    private String fieldMappingJson;

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
