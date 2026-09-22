package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 采购单导出入参（Wave 2B §6.4 / §6.5，只读）。
 *
 * <p>筛选条件复用 {@link PurchaseOrderQueryForm}；分页在导出时由服务端强制改为「第 1 页 + 上限行数」，
 * 因此这里 {@code exportColumns} 只是列勾选的 key 列表，具体落哪些列由后端目录裁决，前端传未知 key 会被忽略。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderExportForm extends PurchaseOrderQueryForm {

    /**
     * 勾选导出的列 key（顺序无关，后端按自身目录固定顺序落表头）；为空表示导出全部列。
     */
    @Size(max = 32)
    private List<String> exportColumns;
}
