package com.xsy.scm.admin.module.business.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类 实体类
 *
 * <p>表名使用 t_product_category：官方示例模块 category 已占用 t_category（CategoryEntity），
 * 按需求约定示例模块需保留，故本表改名避让。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_category")
public class ProductCategoryEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long categoryId;

    /**
     * 父级ID，一级为 0
     */
    private Long parentId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 层级：1 一级，2 二级，3 三级
     */
    private Integer level;

    /**
     * 排序，升序
     */
    private Integer sort;

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
