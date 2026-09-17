package com.xsy.scm.admin.module.business.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务员推广二维码 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_customer_qrcode")
public class CustomerQrcodeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long qrcodeId;

    /**
     * 业务员 ID
     */
    private Long sellerId;

    /**
     * 二维码地址（文件服务，不硬编码公网 URL）
     */
    private String qrcodeUrl;

    /**
     * 扫描次数
     */
    private Integer scanCount;

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
