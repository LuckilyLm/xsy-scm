import { getRequest } from '@/lib/smart-request';

/** 商城单页：公告 / 服务条款 / 关于我们 / 售后规则，只读取已发布内容。 */
export const mallDecorationApi = {
  async page(pageCode) {
    const response = await getRequest(`/scm/mall/decoration/page/${pageCode}`, {}, { silent: true });
    return {
      ...response,
      data: {
        pageCode: response.data?.pageCode ?? pageCode,
        title: response.data?.title ?? '',
        content: response.data?.content ?? '',
      },
    };
  },
};
