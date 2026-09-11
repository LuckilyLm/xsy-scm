import { View, Text, Input, Button } from '@tarojs/components'
import { useRef, useState } from 'react'
import Taro from '@tarojs/taro'
import { login, wechatLogin } from '../../services/auth'
import { useSession } from '../../stores/session'
import { isApiError } from '../../services/http'
import { showApiError } from '../../utils/error'
import { MallErrorCode } from '../../types/mall'
import type { MallProfile } from '../../types/mall'
import './index.css'

const IS_WEAPP = process.env.TARO_ENV === 'weapp'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [wechatLoading, setWechatLoading] = useState(false)
  const session = useSession()
  // 提交中互斥：state 更新是异步的，用 ref 才能真正挡住连点。
  const busyRef = useRef(false)

  const finishLogin = (token: string, profile: MallProfile) => {
    session.login(token, profile)
    Taro.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => Taro.reLaunch({ url: '/pages/home/index' }), 600)
  }

  const onSubmit = async () => {
    if (busyRef.current) return
    if (!username.trim() || !password) {
      Taro.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    busyRef.current = true
    setLoading(true)
    try {
      const res = await login(username.trim(), password)
      finishLogin(res.token, res.profile)
    } catch (e: unknown) {
      // 40170 账号或密码不正确 / 40370 账号停用 / 40371 客户停用 均带回后端原文
      showApiError(e, '登录失败，请稍后重试')
    } finally {
      busyRef.current = false
      setLoading(false)
    }
  }

  const onWechatLogin = async () => {
    if (busyRef.current) return
    if (!IS_WEAPP) {
      Taro.showToast({ title: '请在微信小程序中使用微信登录', icon: 'none' })
      return
    }
    busyRef.current = true
    setWechatLoading(true)
    try {
      const result = await Taro.login()
      if (!result || !result.code) {
        throw new Error('未能获取微信登录凭证，请重试')
      }
      const res = await wechatLogin(result.code)
      finishLogin(res.token, res.profile)
    } catch (e: unknown) {
      if (isApiError(e, MallErrorCode.WECHAT_LOGIN_UNAVAILABLE)) {
        Taro.showToast({ title: '微信登录尚未开通，请使用账号密码登录', icon: 'none' })
      } else {
        showApiError(e, '微信登录失败，请稍后重试')
      }
    } finally {
      busyRef.current = false
      setWechatLoading(false)
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
            disabled={loading}
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
            disabled={loading}
            confirmType="done"
            onConfirm={onSubmit}
            onInput={(e) => setPassword(e.detail.value)}
          />
        </View>
        <Button className="login__btn" loading={loading} disabled={loading} onClick={onSubmit}>
          登录
        </Button>

        {IS_WEAPP && (
          <>
            <View className="login__divider">
              <Text className="login__divider-text">或</Text>
            </View>
            <Button
              className="login__btn login__btn--wechat"
              loading={wechatLoading}
              disabled={wechatLoading}
              onClick={onWechatLogin}
            >
              微信一键登录
            </Button>
          </>
        )}

        <Text className="login__hint">演示账号 demo / Xsy@Demo2026</Text>
      </View>
    </View>
  )
}
