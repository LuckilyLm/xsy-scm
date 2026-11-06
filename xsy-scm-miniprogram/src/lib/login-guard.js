import { USER_TOKEN } from '@/constants/local-storage-key-const';

const LOGIN_PAGE = '/pages/login/login';
const PUBLIC_PAGES = new Set(['/pages/home/index', '/pages/mine/index', LOGIN_PAGE, '/pages-sub/page/index', '/pages-sub/trace/detail']);
let redirecting = false;
let installed = false;
let enteringApp = true;

/** 本地令牌存在即交给服务端核验，避免资料尚在恢复时误判未登录。 */
export function hasUserSession() {
  return Boolean(uni.getStorageSync(USER_TOKEN));
}

/**
 * 统一提示并进入登录页；并发触发只跳转一次，不清理已有会话。
 * @param {string} message 登录原因，省略时提示“请先登录”。
 * @param {boolean} immediate 启动检查时为 true，直接跳转，不等待提示；省略时先提示再跳转。
 */
export function redirectToLogin(message = '请先登录', immediate = false) {
  const pages = getCurrentPages();
  if (redirecting || pages[pages.length - 1]?.route === LOGIN_PAGE.slice(1)) return;
  redirecting = true;
  const openLogin = () => {
    if (hasUserSession()) {
      redirecting = false;
      return;
    }
    uni.reLaunch({ url: LOGIN_PAGE, complete: () => (redirecting = false) });
  };
  if (immediate) {
    openLogin();
  } else {
    uni.showToast({ title: message, icon: 'none' });
    // 给主动点击后的提示留出阅读时间；启动检查不等待这段延时。
    setTimeout(openLogin, 600);
  }
}

/**
 * 检查页面访问；受保护页面缺少令牌时提示并返回 false。
 * @param {string} url 页面路径，可含查询串；为空时不拦截。
 * @returns {boolean} 是否允许进入页面。
 */
export function guardPage(url) {
  if (!url) return true;
  const path = '/' + url.split(/[?#]/)[0].replace(/^\/+/, '');
  if (PUBLIC_PAGES.has(path) || hasUserSession()) return true;
  redirectToLogin();
  return false;
}

/** 检查当前页面，覆盖扫码直达、冷启动与返回已打开页面的情况。 */
export function guardCurrentPage() {
  const pages = getCurrentPages();
  const route = pages[pages.length - 1]?.route;
  // App 的 onShow 可能早于首个页面，等页面路径就绪后再完成一次启动检查。
  if (!route) return true;
  if (enteringApp) {
    enteringApp = false;
    if (!hasUserSession() && ['pages/home/index', 'pages/mine/index'].includes(route)) {
      redirectToLogin(undefined, true);
      return false;
    }
  }
  return guardPage(route);
}

/** 每次从微信重新进入时重做入口检查，覆盖扫码复用后台小程序的情况。 */
export function checkAppLogin() {
  enteringApp = true;
  return guardCurrentPage();
}

/** 统一安装各类页面跳转检查，避免入口各自维护登录条件而遗漏。 */
export function installLoginGuard() {
  if (installed) return;
  installed = true;
  for (const method of ['navigateTo', 'redirectTo', 'reLaunch', 'switchTab']) {
    uni.addInterceptor(method, { invoke: ({ url }) => guardPage(url) });
  }
}
