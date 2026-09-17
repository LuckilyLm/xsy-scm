package com.xsy.scm.admin.module.business.print.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打印模板 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_print_template")
public class PrintTemplateEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long templateId;

    /**
     * 模板编码（PURCHASE / DELIVERY / SORT_TICKET / INQUIRY 等）
     */
    private String templateCode;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 业务类型：1 采购单，2 发货单，3 分拣小票，4 询价报价单
     */
    private Integer bizType;

    /**
     * 模板内容（HTML / 模板引擎，使用 {{key}} 占位）
     */
    private String content;

    /**
     * 纸张规格（A4 / 小票 58mm / 80mm 等）
     */
    private String paperSize;

    /**
     * 是否默认模板：0 否，1 是
     */
    private Integer defaultFlag;

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
