import { PropsWithChildren } from 'react'
import { useLaunch, useDidShow } from '@tarojs/taro'
import Taro from '@tarojs/taro'
import { getToken } from './services/storage'
import './app.css'

function App({ children }: PropsWithChildren) {
  useLaunch(() => {
    // 未登录直接落地登录页（登录页自身不被拦截）
    if (!getToken()) {
      Taro.reLaunch({ url: '/pages/login/index' })
    }
  })

  useDidShow(() => {
    if (!getToken()) {
      const pages = Taro.getCurrentPages()
      const current = pages[pages.length - 1]
      if (current && current.route !== 'pages/login/index') {
        Taro.reLaunch({ url: '/pages/login/index' })
      }
    }
  })

  return children
}

export default App
