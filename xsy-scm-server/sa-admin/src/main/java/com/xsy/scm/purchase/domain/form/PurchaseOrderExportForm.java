package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 采购单导出入参（只读）。
 *
 * <p>
 * 筛选条件复用 {@link PurchaseOrderQueryForm}；分页在导出时由服务端强制改为「第 1 页 + 上限行数」， 因此这里 {@code exportColumns} 只是列勾选的 key
 * 列表，后端列目录决定实际输出，未知 key 会被忽略。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderExportForm extends PurchaseOrderQueryForm {

    /**
     * 勾选导出的列 key（顺序无关，后端按自身目录固定顺序落表头）；为空表示导出全部列。
     */
    @Size(max = 32, message = "导出列不能超过32项")
    private List<String> exportColumns;
}
