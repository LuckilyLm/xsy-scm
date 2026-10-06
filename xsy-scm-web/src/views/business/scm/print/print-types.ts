/**
 * 打印中心前端类型（与后端 print 域 VO 逐字对齐）。
 *
 * 三条口径：
 * 1. 渲染结果里的数量与金额是后端算好的四位定点字符串，前端只渲染、不重算；
 * 2. 单元格以列 key 为键，列顺序由 `columns` 决定，前端不自己决定打印哪些列
 *    （权限剔除在服务端做完，前端剔除等于没剔除）；
 * 3. `hiddenFields` 必须展示：静默少一列会让人以为模板没配好，而真实原因是金额权限。
 */

export type Id = string | number;

export interface PrintPage {
    pageNum: number;
    pageSize: number;
}

export type PrintDocumentType = 'PURCHASE_ORDER' | 'DELIVERY_NOTE' | 'SORTING_TICKET';

export type PrintPaper = 'A4' | 'TICKET_80';

export type PrintOrientation = 'PORTRAIT' | 'LANDSCAPE';

/** `ScmPrintTemplateModel` —— 受控模板模型。 */
export interface PrintTemplateModel {
    title?: string;
    paper?: PrintPaper;
    orientation?: PrintOrientation;
    headerFields?: string[];
    columns?: string[];
    showTotals?: boolean;
    footerNote?: string | null;
}

/** `ScmPrintField` —— 字段目录里的一项（标签与对齐由服务端给出，模板改不了）。 */
export interface PrintField {
    key: string;
    label: string;
    money: boolean;
    numeric: boolean;
}

/** `ScmPrintFieldCatalogVO`。 */
export interface PrintFieldCatalog {
    documentType: PrintDocumentType;
    documentTypeLabel: string;
    headerFields: PrintField[];
    columns: PrintField[];
    totals: PrintField[];
    amountPermissionRequired: boolean;
    amountVisible: boolean;
}

/** `ScmPrintDocumentTypeVO` —— 可配置打印的单据类型（类型下拉用）。 */
export interface PrintDocumentTypeOption {
    documentType: PrintDocumentType;
    documentTypeLabel: string;
    amountPermissionRequired: boolean;
    amountVisible: boolean;
}

/** `ScmPrintTemplateVO`。 */
export interface PrintTemplate {
    id: Id;
    documentType: PrintDocumentType;
    documentTypeLabel?: string;
    templateCode: string;
    templateName: string;
    defaultFlag?: boolean;
    enabledFlag?: boolean;
    model?: PrintTemplateModel;
    remark?: string | null;
    version?: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface PrintTemplateQuery extends PrintPage {
    documentType?: PrintDocumentType;
    keyword?: string;
    enabledFlag?: boolean;
}

export interface PrintTemplateSave {
    id?: Id;
    documentType: PrintDocumentType;
    templateCode: string;
    templateName: string;
    defaultFlag?: boolean;
    enabledFlag?: boolean;
    model: PrintTemplateModel;
    remark?: string | null;
    version?: number;
}

export interface PrintRenderField {
    key: string;
    label: string;
    value: string;
}

export interface PrintRenderColumn {
    key: string;
    label: string;
    numeric: boolean;
}

/**
 * `ScmPrintRenderVO` —— 一份已经算好的版面。
 *
 * `frozen=true` 表示来自冻结快照（正式打印或历史重印），此时 `recordId` / `printedAt` /
 * `printedBy` 非空，且内容不再随业务数据变化。
 */
export interface PrintRender {
    documentType: PrintDocumentType;
    businessId?: Id;
    businessNo?: string;
    templateId?: Id;
    templateCode?: string;
    templateName?: string;
    templateVersion?: number;
    title?: string;
    paper?: PrintPaper;
    orientation?: PrintOrientation;
    footerNote?: string | null;
    showTotals?: boolean;
    headerFields: PrintRenderField[];
    columns: PrintRenderColumn[];
    rows: Record<string, string>[];
    totals: PrintRenderField[];
    hiddenFields?: string[];
    frozen?: boolean;
    recordId?: Id;
    printedAt?: string;
    printedBy?: string;
}

/** `ScmPrintRecordVO`。 */
export interface PrintRecord {
    id: Id;
    documentType: PrintDocumentType;
    documentTypeLabel?: string;
    businessId: Id;
    businessNo?: string;
    templateId: Id;
    templateCode: string;
    templateName: string;
    templateVersion: number;
    printedAt?: string;
    printedBy?: string;
}

export interface PrintRecordQuery extends PrintPage {
    documentType?: PrintDocumentType;
    businessNo?: string;
}
