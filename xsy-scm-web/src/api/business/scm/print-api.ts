/** 打印模板管理与业务单据查看权限分开校验。正式打印使用幂等键防重复记录；预览只读不计次。 */
import {getRequest, postRequest, request, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  Id,
  PrintDocumentType,
  PrintDocumentTypeOption,
  PrintFieldCatalog,
  PrintRecord,
  PrintRecordQuery,
  PrintRender,
  PrintTemplate,
  PrintTemplateQuery,
  PrintTemplateSave,
} from '/@/views/business/scm/print/print-types';

const keys = new Map<string, string>();

/** 幂等 POST：同签名重试复用同一个键，成功后释放。 */
async function printCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
  const signature = path + JSON.stringify(data);
  let key = keys.get(signature);
  if (!key) {
    key = crypto.randomUUID();
    keys.set(signature, key);
  }
  const result = (await request({
    url: path,
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  })) as unknown as ScmResponse<T>;
  keys.delete(signature);
  return result;
}

export const printApi = {
  // ---- 模板 ----
  templateQuery: (data: PrintTemplateQuery, options?: RequestOptions) =>
    postRequest('/scm/print/template/query', data, options) as unknown as Promise<ScmResponse<ScmPage<PrintTemplate>>>,

  templateCatalog: (documentType: PrintDocumentType, options?: RequestOptions) =>
    getRequest('/scm/print/template/catalog', {documentType}, options) as unknown as Promise<ScmResponse<PrintFieldCatalog>>,

  /** 可配置打印的单据类型清单（类型下拉用，不硬编码）。 */
  documentTypes: (options?: RequestOptions) =>
    getRequest('/scm/print/template/document-types', {}, options) as unknown as Promise<ScmResponse<PrintDocumentTypeOption[]>>,

  templateDetail: (id: Id) => getRequest(`/scm/print/template/${id}`, {}) as unknown as Promise<ScmResponse<PrintTemplate>>,

  templateCreate: (data: PrintTemplateSave) => postRequest('/scm/print/template', data) as unknown as Promise<ScmResponse<Id>>,

  templateUpdate: (data: PrintTemplateSave) => postRequest('/scm/print/template/update', data) as unknown as Promise<ScmResponse<string>>,

  templateSetDefault: (id: Id) => postRequest(`/scm/print/template/${id}/default`, {}) as unknown as Promise<ScmResponse<string>>,

  templateDelete: (id: Id, version: number) =>
    postRequest(`/scm/print/template/${id}/delete?version=${version}`, {}) as unknown as Promise<ScmResponse<string>>,

  // ---- 渲染与打印 ----
  templateOptions: (documentType: PrintDocumentType, businessId: Id, options?: RequestOptions) =>
    getRequest(`/scm/print/${documentType}/${businessId}/templates`, {}, options) as unknown as Promise<ScmResponse<PrintTemplate[]>>,

  /** 预览：只读，不计次、不留痕。`templateId` 为空用默认模板。 */
  preview: (documentType: PrintDocumentType, businessId: Id, templateId?: Id, options?: RequestOptions) => {
    const query = templateId === undefined || templateId === null ? '' : `?templateId=${templateId}`;
    return getRequest(`/scm/print/${documentType}/${businessId}/preview${query}`, {}, options) as unknown as Promise<ScmResponse<PrintRender>>;
  },

  /** 正式打印：冻结模板版本与版面快照，返回冻结版面。 */
  print: (documentType: PrintDocumentType, businessId: Id, templateId?: Id) =>
    printCommand<PrintRender>(`/scm/print/${documentType}/${businessId}/print`, {
      templateId: templateId ?? null,
    }),

  /** 历史重印：只读冻结快照，并按当前调用者的金额权限重新剔除。 */
  reprint: (recordId: Id) => getRequest(`/scm/print/record/${recordId}/reprint`, {}) as unknown as Promise<ScmResponse<PrintRender>>,

  recordQuery: (data: PrintRecordQuery, options?: RequestOptions) =>
    postRequest('/scm/print/record/query', data, options) as unknown as Promise<ScmResponse<ScmPage<PrintRecord>>>,
};

export default printApi;
