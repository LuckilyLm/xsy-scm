<!--
  * 登录
  *
  * @Author:    1024创新实验室-主任：卓大
  * @Date:      2022-09-12 22:34:00
  * @Wechat:    zhuda1024
  * @Email:     lab1024@163.com
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
  *
-->
<template>
  <div class="login-container">
    <div class="box-item desc">
      <div class="welcome">
        <p>欢迎登录 鲜蔬源智链</p>
        <p class="desc">
          鲜蔬源智慧供应链管理平台，覆盖商品、客户、供应商、价格、销售订单、采购、收货、
          仓储等全链路业务，支持客户专属定价、实际重量收货与订单全流程追溯。
          <br />
          <br />
          <span class="setence">
            让生鲜供应链的每一环都清晰可见 ：
            <br />
            &nbsp;&nbsp;&nbsp;&nbsp;从客户下单、订单聚合、采购需求到实际称重收货、分拣发运，
            用一套统一的业务链路串联起采购、销售与交付。
            <br />
            数据驱动决策，效率成就新鲜 !<br />
            鲜蔬源，让好食材更快抵达 !<br />
          </span>
        </p>
      </div>
    </div>
    <div class="box-item login">
      <div class="login-title">账号登录</div>
      <a-form ref="formRef" class="login-form" :model="loginForm" :rules="rules">
        <a-form-item name="loginName">
          <a-input v-model:value.trim="loginForm.loginName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item name="emailCode" v-if="emailCodeShowFlag">
          <a-input-group compact>
            <a-input style="width: calc(100% - 110px)" v-model:value="loginForm.emailCode" autocomplete="on" placeholder="请输入邮箱验证码" />
            <a-button @click="sendSmsCode" class="code-btn" type="primary" :disabled="emailCodeButtonDisabled">
              {{ emailCodeTips }}
            </a-button>
          </a-input-group>
        </a-form-item>
        <a-form-item name="password">
          <a-input-password
            v-model:value="loginForm.password"
            autocomplete="on"
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入密码"
          />
        </a-form-item>
        <a-form-item name="captchaCode">
          <a-input class="captcha-input" v-model:value.trim="loginForm.captchaCode" placeholder="请输入验证码" />
          <img class="captcha-img" :src="captchaBase64Image" @click="getCaptcha" />
        </a-form-item>

        <a-form-item>
          <div class="btn" @click="onLogin">登录</div>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>
<script setup lang="ts">
  defineOptions({ name: "SystemLogin" });
  import { message, notification } from 'ant-design-vue';
  import { onMounted, onUnmounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { loginApi } from '/@/api/system/login-api';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { LOGIN_DEVICE_ENUM } from '/@/constants/system/login-device-const';
  import { useUserStore } from '/@/store/modules/system/user';
  import { buildRoutes } from '/@/router/index';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { encryptData } from '/@/lib/encrypt';
  import { localSave } from '/@/utils/local-util';
  import LocalStorageKeyConst from '/@/constants/local-storage-key-const';
  import { useDictStore } from '/@/store/modules/system/dict';
  import { dictApi } from '/@/api/support/dict-api';

  //--------------------- 登录表单 ---------------------------------

  const loginForm = reactive({
    loginName: 'admin',
    password: '',
    captchaCode: '',
    captchaUuid: '',
    loginDevice: LOGIN_DEVICE_ENUM.PC.value,
  });
  const rules = {
    loginName: [{ required: true, message: '用户名不能为空' }],
    password: [{ required: true, message: '密码不能为空' }],
    captchaCode: [{ required: true, message: '验证码不能为空' }],
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

    notification['success']({
      message: '温馨提示',
      description: '鲜蔬源智链 · 智慧供应链管理平台已就绪',
      duration: 8,
      onClick: () => {},
    });
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
