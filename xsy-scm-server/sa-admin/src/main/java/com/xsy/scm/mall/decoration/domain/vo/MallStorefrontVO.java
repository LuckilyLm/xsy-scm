package com.xsy.scm.mall.decoration.domain.vo;

/** 商城公开店铺资料，只包含客户展示字段，不暴露后台版本与操作人信息。 */
public record MallStorefrontVO(String storeName, String storeLink) {
    /**
     * 将后台店铺配置投影为公开资料。
     *
     * @param store
     *            已保存的配置；为空时返回默认商城名称和空链接
     * @return 可匿名展示的店铺名称与链接，不包含维护字段
     */
    public static MallStorefrontVO from(MallStoreVO store) {
        return store == null
            ? new MallStorefrontVO("商城", null)
            : new MallStorefrontVO(store.getStoreName(), store.getStoreLink());
    }
}
