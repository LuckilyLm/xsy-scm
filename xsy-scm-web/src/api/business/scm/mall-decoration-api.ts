import {getRequest, postRequest} from '/@/lib/axios';

/** 商城单页：四个固定编码，客户端按编码取发布内容。 */
export type MallPageCode = 'ANNOUNCEMENT' | 'TERMS' | 'ABOUT' | 'AFTER_SALE';

export interface MallPage {
  pageCode: MallPageCode;
  title: string;
  content: string;
  version: number;
  publishedContent?: string | null;
  publishedVersion?: number | null;
  publishedAt?: string | null;
  publishedBy?: string | null;
  updatedAt?: string;
  updatedBy?: string | null;
  unpublishedChanges?: boolean;
}

export interface MallStore {
  version: number;
  storeName: string;
  storeLink?: string | null;
  updatedAt?: string;
  updatedBy?: string | null;
}

export const mallDecorationApi = {
  page: (pageCode: MallPageCode) => getRequest<MallPage>(`/scm/mall-admin/decoration/page/${pageCode}`, {}),
  savePage: (form: {pageCode: MallPageCode; version: number; title: string; content: string}) =>
    postRequest<MallPage>('/scm/mall-admin/decoration/page', form),
  publishPage: (pageCode: MallPageCode, version: number) => postRequest<MallPage>('/scm/mall-admin/decoration/page/publish', {pageCode, version}),
  store: () => getRequest<MallStore>('/scm/mall-admin/decoration/store', {}),
  saveStore: (form: {version: number; storeName: string; storeLink: string | null}) =>
    postRequest<MallStore>('/scm/mall-admin/decoration/store', form),
};
