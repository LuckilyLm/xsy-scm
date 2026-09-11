// defineAppConfig 由 @tarojs/taro 的全局类型声明提供（tsconfig types 已引入），无需 import。
export default defineAppConfig({
  pages: [
    'pages/login/index',
    'pages/home/index',
    'pages/category/index',
    'pages/cart/index',
    'pages/profile/index',
  ],
  subPackages: [
    {
      root: 'subpackages/account',
      pages: ['address/index', 'address-edit/index'],
    },
    {
      root: 'subpackages/trade',
      pages: ['product/index', 'checkout/index', 'order-list/index', 'order-detail/index'],
    },
  ],
  window: {
    navigationBarBackgroundColor: '#16a34a',
    navigationBarTextStyle: 'white',
    navigationBarTitleText: '鲜蔬源商城',
    backgroundColor: '#f5f7fa',
    backgroundTextStyle: 'dark',
  },
  tabBar: {
    color: '#8a8f99',
    selectedColor: '#16a34a',
    backgroundColor: '#ffffff',
    borderStyle: 'black',
    list: [
      { pagePath: 'pages/home/index', text: '首页' },
      { pagePath: 'pages/category/index', text: '分类' },
      { pagePath: 'pages/cart/index', text: '购物车' },
      { pagePath: 'pages/profile/index', text: '我的' },
    ],
  },
})
