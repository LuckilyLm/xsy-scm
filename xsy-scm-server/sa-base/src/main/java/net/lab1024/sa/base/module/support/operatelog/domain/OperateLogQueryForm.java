package net.lab1024.sa.base.module.support.operatelog.domain;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 操作日志查询 表单
 *
 */
@Data
public class OperateLogQueryForm extends PageParam {

    @Schema(description = "用户ID")
    private Long operateUserId;

    @Schema(description = "用户类型")
    private Integer operateUserType;

    @Schema(description = "关键字：模块、操作内容")
    private String keywords;

    @Schema(description = "请求关键字：请求地址、请求方法、请求参数")
    private String requestKeywords;

    @Schema(description = "开始日期")
    private String startDate;

    @Schema(description = "结束日期")
    private String endDate;


    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "请求结果 false失败 true成功")
    private Boolean successFlag;

    /**
     * 可选的结构化业务对象筛选类型：PRODUCT / CUSTOMER / DELIVERY_ROUTE。
     * 与 {@link #businessId} 成对使用，由 sa-admin 统一入口校验白名单与领域读取权限；
     * 这里的字段只服务精确匹配，不做全文包含检索。
     */
    @Schema(description = "业务对象类型：PRODUCT / CUSTOMER / DELIVERY_ROUTE")
    private String businessType;

    @Schema(description = "业务对象 ID，与 businessType 成对提供")
    private Long businessId;

}
