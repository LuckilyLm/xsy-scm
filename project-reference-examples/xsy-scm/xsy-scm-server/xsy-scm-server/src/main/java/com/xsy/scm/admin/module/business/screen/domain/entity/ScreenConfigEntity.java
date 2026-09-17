package com.xsy.scm.admin.module.business.screen.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据大屏配置 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_screen_config")
public class ScreenConfigEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long screenId;

    /**
     * 大屏编码，见 ScreenCodeEnum
     */
    private Integer screenCode;

    /**
     * 大屏名称
     */
    private String screenName;

    /**
     * 布局与指标配置（JSON）
     */
    private String layoutJson;

    /**
     * 刷新间隔（秒）
     */
    private Integer refreshInterval;

    /**
     * 排序维度（部分大屏使用，如分拣绩效按包裹数 / 分拣数量 / 分拣重量）
     */
    private String sortField;

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
