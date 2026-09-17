package com.xsy.scm.admin.module.business.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商账号 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_supplier_account")
public class SupplierAccountEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long accountId;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 登录账号
     */
    private String account;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 密码（加密存储，禁止明文）
     */
    private String password;

    /**
     * 微信 openid（小程序登录用）
     */
    private String openid;

    /**
     * 登录态版本号，改密/禁用后 +1，使历史登录态失效
     */
    private Integer loginVersion;

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
