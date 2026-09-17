import { postRequest } from '/@/lib/axios';

export interface PurchaseGenerateParam {
  startTime: string;
  endTime: string;
  calculateStock: boolean;
}

export interface PurchaseGeneratePreviewItem {
  productId: number;
  productName: string;
  skuId: number;
  requireQuantity: number;
  stockQuantity: number;
  purchaseQuantity: number;
  unitPrice: number;
}

export interface PurchaseGeneratePreview {
  supplierId: number;
  supplierName: string;
  items: PurchaseGeneratePreviewItem[];
}

export const purchaseGenerateApi = {
  // 预览汇总
  preview: (param: PurchaseGenerateParam) => postRequest<PurchaseGeneratePreview[]>('/purchase/generate/preview', param),
  // 按汇总落库生成采购单
  generate: (param: PurchaseGenerateParam) => postRequest<string>('/purchase/generate', param),
};
