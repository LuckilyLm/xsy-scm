import { View, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { logout as logoutApi } from '../../services/auth'
import { useSession } from '../../stores/session'
import './index.css'

export default function ProfilePage() {
  const session = useSession()
  const profile = session.profile

  useDidShow(() => {
    // 进入页面时刷新一次会话态（例如从其他端变更）
    session.bootstrap()
  })

  const onLogout = () => {
    Taro.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await logoutApi()
        } catch {
          /* 即使后端失败也清理本地会话 */
        }
        session.logout()
        Taro.reLaunch({ url: '/pages/login/index' })
      },
    })
  }

  const goAddress = () =>
    Taro.navigateTo({ url: '/subpackages/account/address/index' })
  const goOrders = () =>
    Taro.navigateTo({ url: '/subpackages/trade/order-list/index' })

  return (
    <View className="profile">
      <View className="profile__header">
        <View className="profile__avatar">
          <Text>{profile?.customerName?.slice(0, 1) ?? '客'}</Text>
        </View>
        <View className="profile__info">
          <Text className="profile__name">{profile?.customerName ?? '未登录'}</Text>
          <Text className="profile__code">
            {profile?.customerCode ? `客户编码：${profile.customerCode}` : ' '}
          </Text>
          {profile?.wechatBound && <Text className="profile__wx">已绑定微信</Text>}
        </View>
      </View>

      <View className="profile__menu">
        <View className="profile__row" onClick={goAddress}>
          <Text>收货地址</Text>
          <Text className="profile__arrow">›</Text>
        </View>
        <View className="profile__row" onClick={goOrders}>
          <Text>我的订单</Text>
          <Text className="profile__arrow">›</Text>
        </View>
      </View>

      <View className="profile__logout" onClick={onLogout}>
        <Text>退出登录</Text>
      </View>
    </View>
  )
}
