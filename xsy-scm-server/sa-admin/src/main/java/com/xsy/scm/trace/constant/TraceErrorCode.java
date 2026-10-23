package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 追溯域错误码：批次档案与收货批次的失败原因。
 *
 * <p>
 * 批次数量与收货量的核对属于采购收货命令的校验，错误码定义在采购域；这里只放批次自身的问题。
 */
@Getter
@RequiredArgsConstructor
public enum TraceErrorCode implements ScmErrorCode {

    TRACE_LOT_NOT_FOUND(41500, "批次不存在"),

    TRACE_LOT_SOURCE_CODE_INVALID(41501, "来源生产批号长度不能超过 64 个字符"),

    TRACE_LOT_QUANTITY_INVALID(41502, "批次数量必须为大于零的四位定点数"),

    TRACE_LOT_REQUIRED(41503, "批次仓的收货必须逐行登记批次"),

    /** 没有单元出库记录前，不能凭批次出库推断某枚码已离场并释放发码额度。 */
    TRACE_UNIT_EXCEED_BALANCE(41504, "累计已发码量与本次发码之和不能超过当前批次余额"),

    TRACE_UNIT_NOT_FOUND(41505, "包装码不存在"),

    TRACE_UNIT_QUANTITY_INVALID(41506, "每包数量必须为大于零的四位定点数"),

    /** 一码一物，绑定后不能改绑。 */
    TRACE_UNIT_ALREADY_BOUND(41507, "该包装码已经绑定过分拣任务行，不能重复绑定"),

    TRACE_UNIT_SKU_MISMATCH(41508, "包装码所属批次与分拣行的商品规格不一致"),

    TRACE_UNIT_BIND_EXCEED(41509, "该分拣行已绑定的数量将超过实拣数量"),

    TRACE_SORTING_ITEM_NOT_FOUND(41510, "分拣任务行不存在"),

    TRACE_UNIT_VOID(41511, "包装码已作废，不能再绑定"),

    /** 已绑定的码不能作废，绑定记录不撤销。 */
    TRACE_UNIT_VOID_BOUND(41512, "已绑定分拣任务行的包装码不能作废，请先走退货或更正流程"),

    TRACE_UNIT_ALREADY_VOID(41513, "包装码已作废，不能重复作废"),

    TRACE_UNIT_VOID_REASON_REQUIRED(41514, "作废原因不能为空"),

    /** 只有进行中或已完成的分拣任务才能承接实物。 */
    TRACE_SORTING_TASK_STATE_INVALID(41515, "该分拣任务未开始或已取消，不能绑定包装码"),

    /** 包装码的批次必须来自分拣任务所在仓库。 */
    TRACE_UNIT_WAREHOUSE_MISMATCH(41516, "包装码所属批次与分拣任务不在同一仓库，不能绑定"),

    /** 实拣量是销售单位、包装码量是库存记账单位；缺少当时保存的换算关系时，两种量不能直接比较。 */
    TRACE_UNIT_UNIT_MISMATCH(41517, "包装码的计量单位与分拣行销售单位之间没有可用的冻结换算，不能绑定"),

    /** 更正申请不存在。 */
    TRACE_CORRECTION_NOT_FOUND(41520, "更正申请不存在"),

    /** 同一个码只允许一条待审批的更正申请。 */
    TRACE_CORRECTION_PENDING_EXISTS(41521, "该包装码已有一条待审批的更正申请"),

    /** 申请与审批不能是同一个人。 */
    TRACE_CORRECTION_SELF_APPROVAL(41522, "不能审批自己提交的更正申请"),

    TRACE_CORRECTION_STATE_INVALID(41523, "更正申请已处理，不能重复审批"),

    TRACE_CORRECTION_NO_BINDING(41524, "该包装码当前没有可撤销的绑定"),

    /** 已有正式出库记录，不再允许解绑后重新绑定到其他订单。 */
    TRACE_CORRECTION_ALREADY_OUTBOUND(41525, "该包装码原绑定订单行已正式出库，不能解绑，请走退货或售后补偿"),

    TRACE_UNIT_ALREADY_OUTBOUND(41526, "该分拣行已正式出库，不可再补绑包装码"),

    /** 附件只能挂到未删除的商品上，已删商品不接收新资质。 */
    TRACE_PRODUCT_NOT_FOUND(41527, "商品不存在或已删除"),

    /** 附件标识必须在文件表里真实存在，否则会写出谁也读不到的关系行。 */
    TRACE_ATTACHMENT_NOT_FOUND(41528, "附件不存在，请重新上传后再保存"),

    /** 净重只能补录一次；已有重量的码要走称重更正，不能覆盖。 */
    TRACE_UNIT_WEIGHT_ALREADY_SET(41529, "该包装码已有净重，不能重复补录"),

    /** 称重记录不存在或不属于该分拣行。 */
    TRACE_WEIGHING_EVENT_NOT_FOUND(41530, "称重记录不存在或不属于该分拣任务行"),

    /** 没有净重的码无法自动绑定，重量是「这一筐装了多少」的唯一依据。 */
    TRACE_UNIT_WEIGHT_REQUIRED(41531, "该包装码没有净重，不能按称重结果绑定"),

    /** 净重与每包数量是两列，校验规则相同但提示必须分开，否则补录时看不懂在说什么。 */
    TRACE_UNIT_WEIGHT_INVALID(41532, "净重必须为大于零的四位定点数"),

    /** 公开页不区分「码不存在」与「码已删除」，避免被用来枚举探测。 */
    TRACE_PUBLIC_TOKEN_NOT_FOUND(41533, "未找到该溯源码，请确认标签完整"),

    /** 扫码页免登录，限流是唯一的滥用防线。 */
    TRACE_PUBLIC_RATE_LIMITED(41534, "查询过于频繁，请稍后再试"),

    /** 只能下载挂在该码所属批次或商品上的文件，不能拿 token 当作任意文件的通行证。 */
    TRACE_PUBLIC_FILE_NOT_ALLOWED(41535, "该文件不属于这个溯源码"),

    /** 已集合完成的筐不再补发码，否则分拣结果与包装身份会对不上。 */
    TRACE_WEIGHING_EVENT_COMPLETED(41536, "该称重筐已完成集合确认，不能再发码"),

    TRACE_REPORT_NOT_FOUND(41537, "检测报告不存在或无权访问"),
    TRACE_REPORT_STATE_INVALID(41538, "报告已变更，请刷新；只有草稿可以编辑和发布，已发布报告可以撤回"),
    TRACE_REPORT_DATE_INVALID(41539, "检测日期不能晚于今天，有效期不能早于检测日期；过期报告不能发布"),
    TRACE_REPORT_FILE_INVALID(41540, "请上传有权访问的私有PDF或图片，单个不超过20MB；发布时至少需要一个附件"),
    TRACE_REPORT_NO_EXISTS(41541, "报告编号已存在，请刷新列表或核对编号"),
    TRACE_REPORT_LOT_IMMUTABLE(41542, "报告所属批次不能修改，请在正确批次下新建报告");

    private final int code;

    private final String msg;
}
