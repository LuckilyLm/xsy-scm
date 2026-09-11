import { defineConfig } from '@tarojs/cli'
import type { UserConfigExport } from '@tarojs/cli'
import devConfig from './dev'
import prodConfig from './prod'

// 后端商城接口基础地址（注入到代码中的编译期常量）。
// 开发期：H5 用 '/api' 走 devServer 代理；小程序填真实可访问域名。
const API_BASE = process.env.TARO_APP_API_BASE || 'https://api.example.com'

const config: UserConfigExport = {
  projectName: 'xsy-scm-miniapp',
  date: '2026-9-10',
  designWidth: 750,
  deviceRatio: {
    640: 2.34 / 2,
    750: 1,
    828: 1.81 / 2,
  },
  sourceRoot: 'src',
  outputRoot: 'dist',
  plugins: [],
  defineConstants: {
    'process.env.TARO_APP_API_BASE': JSON.stringify(API_BASE),
  },
  copy: {
    patterns: [],
    options: {},
  },
  framework: 'react',
  compiler: 'webpack5',
  cache: {
    enable: false,
  },
  mini: {
    postcss: {
      pxtransform: {
        enable: true,
        config: {},
      },
      cssModules: {
        enable: false,
        config: {
          namingPattern: 'module',
          generateScopedName: '[name]__[local]___[hash:base64:5]',
        },
      },
    },
  },
  h5: {
    publicPath: '/',
    staticDirectory: 'static',
    output: {
      filename: 'js/[name].[hash:8].js',
      chunkFilename: 'js/[name].[hash:8].js',
    },
    postcss: {
      autoprefixer: {
        enable: true,
      },
      cssModules: {
        enable: false,
      },
    },
    devServer: {
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
  },
  rn: {
    appName: 'xsyScmMiniapp',
    postcss: {
      cssModules: {
        enable: false,
      },
    },
  },
}

export default defineConfig((merge, { command, mode }) => {
  if (process.env.NODE_ENV === 'development') {
    return merge({}, config, devConfig)
  }
  return merge({}, config, prodConfig)
})
