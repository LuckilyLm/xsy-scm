import { apiClient } from './http';
import type { PurchaseDemand,PurchaseOrder,PurchaseOrderPayload,PurchaseReceipt,PurchaseReceiptConfirmation,PurchaseReceiptItem,ReceiptConfirmPayload } from '../types/purchase';
const config=(key:string)=>({headers:{'Idempotency-Key':key}});
export async function fetchPurchaseDemands(){return (await apiClient.get<PurchaseDemand[]>('/purchase-demands')).data;}
export async function generatePurchaseDemands(salesOrderIds:number[],key:string){return (await apiClient.post<number[]>('/purchase-demands/generate',{salesOrderIds},config(key))).data;}
export async function fetchPurchaseOrders(){return (await apiClient.get<PurchaseOrder[]>('/purchase-orders')).data;}
export async function fetchPurchaseOrder(id:number){return (await apiClient.get<PurchaseOrder>(`/purchase-orders/${id}`)).data;}
export async function createPurchaseOrder(payload:PurchaseOrderPayload,key:string){return (await apiClient.post<number>('/purchase-orders',payload,config(key))).data;}
export async function updatePurchaseOrder(id:number,payload:PurchaseOrderPayload){await apiClient.put(`/purchase-orders/${id}`,payload);}
export async function submitPurchaseOrder(id:number,version:number,key:string){await apiClient.post(`/purchase-orders/${id}/submit`,{version},config(key));}
export async function cancelPurchaseOrder(id:number,version:number,reason:string,key:string){await apiClient.post(`/purchase-orders/${id}/cancel`,{version,reason},config(key));}
export async function fetchReceipts(){return (await apiClient.get<PurchaseReceipt[]>('/purchase-receipts')).data;}
export async function createReceipt(purchaseOrderId:number,remark?:string){return (await apiClient.post<number>('/purchase-receipts',{purchaseOrderId,remark})).data;}
export async function fetchReceipt(id:number){return (await apiClient.get<PurchaseReceipt>(`/purchase-receipts/${id}`)).data;}
export async function fetchReceiptItems(id:number){return (await apiClient.get<PurchaseReceiptItem[]>(`/purchase-receipts/${id}/items`)).data;}
export async function fetchReceiptConfirmations(id:number){return (await apiClient.get<PurchaseReceiptConfirmation[]>(`/purchase-receipts/${id}/confirmations`)).data;}
export async function confirmReceipt(id:number,payload:ReceiptConfirmPayload,key:string){return (await apiClient.post(`/purchase-receipts/${id}/confirm`,payload,config(key))).data;}
