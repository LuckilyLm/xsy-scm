import {getRequest} from '@/lib/xsy-request';

/** 商城单页：公告 / 服务条款 / 关于我们 / 售后规则，只读取已发布内容。 */
export const mallDecorationApi = {
  /** 只读公开店铺名称和链接，忽略客户登录态，避免展示配置干扰客户会话。 */
  store: () => getRequest('/scm/mall/decoration/store', {}, {anonymous: true, silent: true}),
  async page(pageCode) {
    const response = await getRequest(`/scm/mall/decoration/page/${pageCode}`, {}, {anonymous: true, silent: true});
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
