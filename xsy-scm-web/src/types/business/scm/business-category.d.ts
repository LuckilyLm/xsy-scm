/**
 * 客户 / 供应商业务分类前端类型（对应后端 businesscategory 域的 Form / VO）。
 *
 * 分类是客户与供应商共用的可维护小树，但按 scope 各自成树；不要与商品分类混用。
 */

export type ScmId = string | number;

export type BusinessCategoryScope = 'CUSTOMER' | 'SUPPLIER';

export type BusinessCategoryStatus = 'ENABLED' | 'DISABLED';

export interface BusinessCategory {
  categoryId: ScmId;
  version?: number;
  scope: BusinessCategoryScope;
  parentId?: ScmId | null;
  categoryCode: string;
  name: string;
  level: number;
  sortOrder: number;
  status: BusinessCategoryStatus;
  categoryPath?: string;
  children?: BusinessCategory[];
}

export interface BusinessCategoryForm {
  categoryId?: ScmId;
  version?: number;
  scope: BusinessCategoryScope;
  parentId?: ScmId | null;
  categoryCode: string;
  name: string;
  sortOrder: number;
  status: BusinessCategoryStatus;
}
