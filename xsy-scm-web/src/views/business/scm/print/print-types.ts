/** 打印内容中的金额、数量是服务端生成的四位小数字符串。表头顺序与金额权限由服务端决定，前端只负责展示。 */

export type Id = string | number;

export interface PrintPage {
  pageNum: number;
  pageSize: number;
}

export type PrintDocumentType =
  | 'PURCHASE_ORDER'
  | 'DELIVERY_NOTE'
  | 'SORTING_TICKET'
  | 'SALES_ORDER'
  | 'TRACE_UNIT'
  | 'STATEMENT'
  | 'CUSTOMER_SORTING_SHEET'
  | 'CUSTOMER_DELIVERY_NOTE';

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
  /** 是否支持按客户绑定专属模板。 */
  customerBindable: boolean;
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
  /** 是否为当前单据客户的专属模板；仅模板选项接口会填。 */
  customerBound?: boolean;
  model?: PrintTemplateModel;
  remark?: string | null;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** `ScmPrintCustomerTemplateVO` —— 客户专属模板绑定。 */
export interface PrintCustomerTemplate {
  id: Id;
  customerId: Id;
  customerName?: string;
  documentType: PrintDocumentType;
  documentTypeLabel?: string;
  templateId: Id;
  templateCode?: string;
  templateName?: string;
  templateEnabled?: boolean;
  version?: number;
  updatedAt?: string;
}

/** 绑定 / 换绑请求；一个客户对一种单据类型只保留一条有效绑定。 */
export interface PrintCustomerTemplateSave {
  customerId: Id;
  documentType: PrintDocumentType;
  templateId: Id;
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

/** 一份已算好的打印版面；frozen=true 表示来自冻结打印记录，内容不再变化。 */
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

/** 批量打印请求：按配送日期取当天已确认订单，也可直接给订单 id 重印。 */
export interface PrintBatchRequest {
  documentType: 'CUSTOMER_SORTING_SHEET' | 'CUSTOMER_DELIVERY_NOTE';
  businessDate: string;
  routeId?: Id | null;
  customerIds?: Id[];
  orderIds?: Id[];
  templateId?: Id | null;
  batchKey?: string;
}

export interface PrintBatchItem {
  orderId: Id;
  orderNo?: string;
  customerName?: string;
  success: boolean;
  recordId?: Id;
  message?: string;
}

export interface PrintBatchResult {
  total: number;
  succeeded: number;
  failed: number;
  items: PrintBatchItem[];
  renders: PrintRender[];
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
