/** 打印模板管理与单据查看权限分开；正式打印用幂等键防重复，预览不计次。 */
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
  const result = await request<T>({
    url: path,
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  });
  keys.delete(signature);
  return result;
}

export const printApi = {
  // ---- 模板 ----
  templateQuery: (data: PrintTemplateQuery, options?: RequestOptions) =>
    postRequest<ScmPage<PrintTemplate>>('/scm/print/template/query', data, options),

  templateCatalog: (documentType: PrintDocumentType, options?: RequestOptions) =>
    getRequest<PrintFieldCatalog>('/scm/print/template/catalog', {documentType}, options),

  /** 可配置打印的单据类型，供类型下拉使用。 */
  documentTypes: (options?: RequestOptions) => getRequest<PrintDocumentTypeOption[]>('/scm/print/template/document-types', {}, options),

  templateDetail: (id: Id) => getRequest<PrintTemplate>(`/scm/print/template/${id}`, {}),

  templateCreate: (data: PrintTemplateSave) => postRequest<Id>('/scm/print/template', data),

  templateUpdate: (data: PrintTemplateSave) => postRequest<string>('/scm/print/template/update', data),

  templateSetDefault: (id: Id) => postRequest<string>(`/scm/print/template/${id}/default`, {}),

  templateDelete: (id: Id, version: number) => postRequest<string>(`/scm/print/template/${id}/delete?version=${version}`, {}),

  // ---- 渲染与打印 ----
  templateOptions: (documentType: PrintDocumentType, businessId: Id, options?: RequestOptions) =>
    getRequest<PrintTemplate[]>(`/scm/print/${documentType}/${businessId}/templates`, {}, options),

  /** 预览：只读，不计次、不留痕。`templateId` 为空用默认模板。 */
  preview: (documentType: PrintDocumentType, businessId: Id, templateId?: Id, options?: RequestOptions) => {
    const query = templateId === undefined || templateId === null ? '' : `?templateId=${templateId}`;
    return getRequest<PrintRender>(`/scm/print/${documentType}/${businessId}/preview${query}`, {}, options);
  },

  /** 正式打印：保存模板版本和版面内容，返回本次打印版面。 */
  print: (documentType: PrintDocumentType, businessId: Id, templateId?: Id) =>
    printCommand<PrintRender>(`/scm/print/${documentType}/${businessId}/print`, {
      templateId: templateId ?? null,
    }),

  /** 历史重印：使用当时保存的内容，金额按当前权限重新过滤。 */
  reprint: (recordId: Id) => getRequest<PrintRender>(`/scm/print/record/${recordId}/reprint`, {}),

  recordQuery: (data: PrintRecordQuery, options?: RequestOptions) => postRequest<ScmPage<PrintRecord>>('/scm/print/record/query', data, options),
};

export default printApi;
