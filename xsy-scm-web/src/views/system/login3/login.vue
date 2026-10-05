<!--
  * 登录
  *
  *
-->
<template>
  <main class="xsy-login">
    <section class="xsy-login__shell">
      <aside class="xsy-login__brand">
        <img class="xsy-login__logo" :src="logoImg" alt="鲜蔬源智链"/>

        <div class="xsy-login__brand-copy">
          <h1 class="xsy-login__headline">鲜蔬源智链</h1>
          <p class="xsy-login__tagline">智慧供应链管理平台</p>
        </div>

        <p class="xsy-login__description">一套系统，贯通商品、订单、采购、库存、配送与结算。</p>

        <div class="xsy-login__visual" aria-hidden="true">
          <div class="xsy-login__flow-row">
            <div class="xsy-login__node">
              <AppstoreOutlined class="xsy-login__node-icon"/>
              <span>商品</span>
            </div>
            <div class="xsy-login__node">
              <ShopOutlined class="xsy-login__node-icon"/>
              <span>供应商</span>
            </div>
            <div class="xsy-login__connector"></div>
            <div class="xsy-login__node">
              <InboxOutlined class="xsy-login__node-icon"/>
              <span>仓库</span>
            </div>
            <div class="xsy-login__connector"></div>
            <div class="xsy-login__node">
              <SlidersOutlined class="xsy-login__node-icon"/>
              <span>分拣</span>
            </div>
          </div>
          <div class="xsy-login__flow-row">
            <div class="xsy-login__node">
              <CarOutlined class="xsy-login__node-icon"/>
              <span>配送</span>
            </div>
            <div class="xsy-login__connector"></div>
            <div class="xsy-login__node">
              <TeamOutlined class="xsy-login__node-icon"/>
              <span>客户</span>
            </div>
            <div class="xsy-login__connector"></div>
            <div class="xsy-login__node">
              <AccountBookOutlined class="xsy-login__node-icon"/>
              <span>财务</span>
            </div>
          </div>
        </div>
      </aside>

      <section class="xsy-login__panel">
        <header class="xsy-login__header">
          <h2 class="xsy-login__title">欢迎回来</h2>
          <p class="xsy-login__subtitle">登录鲜蔬源智链管理后台</p>
        </header>

        <a-form ref="formRef" class="xsy-login__form" layout="vertical" :model="loginForm" :rules="rules">
          <a-form-item name="loginName" label="用户名">
            <a-input
                v-model:value.trim="loginForm.loginName"
                placeholder="请输入用户名"
                autocomplete="username"
            >
              <template #prefix>
                <UserOutlined class="xsy-login__field-icon"/>
              </template>
            </a-input>
          </a-form-item>
          <a-form-item name="emailCode" label="邮箱验证码" v-if="emailCodeShowFlag">
            <a-input-group compact>
              <a-input style="width: calc(100% - 120px)" v-model:value="loginForm.emailCode" autocomplete="on"
                       placeholder="请输入邮箱验证码"/>
              <a-button @click="sendSmsCode" class="xsy-login__code-btn" type="primary" :disabled="emailCodeButtonDisabled">
                {{ emailCodeTips }}
              </a-button>
            </a-input-group>
          </a-form-item>
          <a-form-item name="password" label="密码">
            <a-input-password
                v-model:value="loginForm.password"
                autocomplete="current-password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
            >
              <template #prefix>
                <LockOutlined class="xsy-login__field-icon"/>
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item name="captchaCode" label="验证码">
            <div class="xsy-login__captcha">
              <a-input
                  class="xsy-login__captcha-input"
                  v-model:value.trim="loginForm.captchaCode"
                  placeholder="请输入验证码"
                  autocomplete="off"
              >
                <template #prefix>
                  <SafetyCertificateOutlined class="xsy-login__field-icon"/>
                </template>
              </a-input>
              <a-tooltip title="点击刷新验证码">
                <img class="xsy-login__captcha-img" :src="captchaBase64Image" alt="登录验证码" @click="getCaptcha"/>
              </a-tooltip>
            </div>
          </a-form-item>
          <a-form-item class="xsy-login__remember">
            <a-checkbox v-model:checked="rememberPwd">记住密码</a-checkbox>
          </a-form-item>
          <a-form-item class="xsy-login__submit-item">
            <a-button type="primary" block class="xsy-login__submit" @click="onLogin">登录</a-button>
          </a-form-item>
        </a-form>
      </section>
    </section>

    <footer class="xsy-login__footer">© 2026 鲜蔬源智慧供应链管理平台</footer>
  </main>
</template>
<script setup lang="ts">
defineOptions({name: "SystemLoginThree"});
import {
  AccountBookOutlined,
  AppstoreOutlined,
  CarOutlined,
  InboxOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  SlidersOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue';
import {message} from 'ant-design-vue';
import {onMounted, onUnmounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {loginApi} from '/@/api/system/login-api';
import logoImg from '/@/assets/images/logo/xsy-logo.png';
import {SmartLoading} from '/@/components/framework/smart-loading';
import {LOGIN_DEVICE_ENUM} from '/@/constants/system/login-device-const';
import {useUserStore} from '/@/store/modules/system/user';
import {buildRoutes} from '/@/router/index';
import {smartSentry} from '/@/lib/smart-sentry';
import {encryptData} from '/@/lib/encrypt';
import {localSave} from '/@/utils/local-util';
import LocalStorageKeyConst from '/@/constants/local-storage-key-const';
import {useDictStore} from '/@/store/modules/system/dict';
import {dictApi} from '/@/api/support/dict-api';

//--------------------- 登录表单 ---------------------------------

const loginForm = reactive({
  loginName: 'admin',
  password: '',
  captchaCode: '',
  captchaUuid: '',
  loginDevice: LOGIN_DEVICE_ENUM.PC.value,
});
const rules = {
  loginName: [{required: true, message: '用户名不能为空'}],
  password: [{required: true, message: '密码不能为空'}],
  captchaCode: [{required: true, message: '验证码不能为空'}],
};

const showPassword = ref(false);
const router = useRouter();
const formRef = ref();
const rememberPwd = ref(false);

onMounted(() => {
  document.onkeyup = (e) => {
    if (e.keyCode === 13) {
      onLogin();
    }
  };
});

onUnmounted(() => {
  document.onkeyup = null;
});

//登录
async function onLogin() {
  formRef.value.validate().then(async () => {
    try {
      SmartLoading.show();
      // 密码加密
      let encryptPasswordForm = Object.assign({}, loginForm, {
        password: encryptData(loginForm.password),
      });
      const res = await loginApi.login(encryptPasswordForm);
      stopRefreshCaptchaInterval();
      localSave(LocalStorageKeyConst.USER_TOKEN, res.data.token ? res.data.token : '');
      message.success('登录成功');
      //更新用户信息到pinia
      useUserStore().setUserLoginInfo(res.data);
      // 初始化数据字典
      const dictRes = await dictApi.getAllDictData();
      useDictStore().initData(dictRes.data);
      //构建系统的路由
      buildRoutes();
      router.push('/home');
    } catch (e) {
      if (e.data && e.data.code !== 0) {
        loginForm.captchaCode = '';
        getCaptcha();
      }
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  });
}

//--------------------- 验证码 ---------------------------------

const captchaBase64Image = ref('');

async function getCaptcha() {
  try {
    let captchaResult = await loginApi.getCaptcha();
    captchaBase64Image.value = captchaResult.data.captchaBase64Image;
    loginForm.captchaUuid = captchaResult.data.captchaUuid;
    beginRefreshCaptchaInterval(captchaResult.data.expireSeconds);
  } catch (e) {
    console.log(e);
  }
}

let refreshCaptchaInterval = null;

function beginRefreshCaptchaInterval(expireSeconds) {
  if (refreshCaptchaInterval === null) {
    refreshCaptchaInterval = setInterval(getCaptcha, (expireSeconds - 5) * 1000);
  }
}

function stopRefreshCaptchaInterval() {
  if (refreshCaptchaInterval != null) {
    clearInterval(refreshCaptchaInterval);
    refreshCaptchaInterval = null;
  }
}

onMounted(() => {
  getCaptcha();
  getTwoFactorLoginFlag();
});

//--------------------- 邮箱验证码 ---------------------------------

const emailCodeShowFlag = ref(false);
let emailCodeTips = ref('获取邮箱验证码');
let emailCodeButtonDisabled = ref(false);
// 定时器
let countDownTimer = null;

// 开始倒计时
function runCountDown() {
  emailCodeButtonDisabled.value = true;
  let countDown = 60;
  emailCodeTips.value = `${countDown}秒后重新获取`;
  countDownTimer = setInterval(() => {
    if (countDown > 1) {
      countDown--;
      emailCodeTips.value = `${countDown}秒后重新获取`;
    } else {
      clearInterval(countDownTimer);
      emailCodeButtonDisabled.value = false;
      emailCodeTips.value = '获取验证码';
    }
  }, 1000);
}

// 获取双因子登录标识
async function getTwoFactorLoginFlag() {
  try {
    let result = await loginApi.getTwoFactorLoginFlag();
    emailCodeShowFlag.value = result.data;
  } catch (e) {
    smartSentry.captureError(e);
  }
}

// 发送邮箱验证码
async function sendSmsCode() {
  try {
    SmartLoading.show();
    let result = await loginApi.sendLoginEmailCode(loginForm.loginName);
    message.success('验证码发送成功!请登录邮箱查看验证码~');
    runCountDown();
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    SmartLoading.hide();
  }
}
</script>
<style lang="less" scoped>
@import './login.less';
</style>
