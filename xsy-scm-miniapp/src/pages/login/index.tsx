import { View, Text, Input, Button } from '@tarojs/components'
import { useState } from 'react'
import Taro from '@tarojs/taro'
import { login } from '../../services/auth'
import { useSession } from '../../stores/session'
import './index.css'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const session = useSession()

  const onSubmit = async () => {
    if (!username || !password) {
      Taro.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    setLoading(true)
    try {
      const res = await login(username.trim(), password)
      session.login(res.token, res.profile)
      Taro.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => Taro.reLaunch({ url: '/pages/home/index' }), 600)
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '登录失败'
      Taro.showToast({ title: msg, icon: 'none' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <View className="login">
      <View className="login__brand">
        <Text className="login__logo">鲜蔬源</Text>
        <Text className="login__sub">智慧供应链 · 客户商城</Text>
      </View>
      <View className="login__form">
        <View className="field">
          <Text className="field__label">账号</Text>
          <Input
            className="field__input"
            placeholder="客户采购账号"
            value={username}
            onInput={(e) => setUsername(e.detail.value)}
          />
        </View>
        <View className="field">
          <Text className="field__label">密码</Text>
          <Input
            className="field__input"
            placeholder="请输入密码"
            password
            value={password}
            onInput={(e) => setPassword(e.detail.value)}
          />
        </View>
        <Button className="login__btn" loading={loading} onClick={onSubmit}>
          登录
        </Button>
        <Text className="login__hint">演示账号 demo / Xsy@Demo2026</Text>
      </View>
    </View>
  )
}
