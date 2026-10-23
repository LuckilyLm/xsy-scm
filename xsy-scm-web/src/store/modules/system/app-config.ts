/* 应用配置 */
import {defineStore} from 'pinia';
import {appDefaultConfig} from '/@/config/app-config';
import localStorageKeyConst from '/@/constants/local-storage-key-const';
import {smartSentry} from '/@/lib/smart-sentry';
import {AppConfig} from '/@/types/config';
import {localRead} from '/@/utils/local-util';

/** 各版本改过默认值的配置项；迁移时只重置这些键，其余保留用户设置。 */
const CONFIG_MIGRATIONS: Record<number, string[]> = {
  // 6：侧栏宽度 208 → 168、主题色改极光绿、标签页位置改顶部、默认隐藏水印与帮助文档。
  6: ['sideMenuWidth', 'colorIndex', 'pageTagLocation', 'watermarkFlag', 'helpDocFlag'],
  // 7：页脚默认关闭（页脚只有版权行，却每页占一行高度）。
  7: ['footerFlag'],
  // 8：品牌名称单独判断，不能覆盖用户自行设置的网站名称。
  8: [],
};

/** 把旧版本的缓存配置逐版升级到当前版本。 */
function migrateConfig(cached: Record<string, any>): AppConfig {
  const next: Record<string, any> = {...appDefaultConfig, ...cached};
  for (let version = cached.configVersion + 1; version <= appDefaultConfig.configVersion; version += 1) {
    for (const key of CONFIG_MIGRATIONS[version] ?? []) {
      next[key] = (appDefaultConfig as Record<string, any>)[key];
    }
    if (version === 8 && next.websiteName === '鲜蔬源智链') {
      next.websiteName = appDefaultConfig.websiteName;
    }
  }
  next.configVersion = appDefaultConfig.configVersion;
  return next as AppConfig;
}

let state = {
  ...appDefaultConfig,
};

let appConfigStr = localRead(localStorageKeyConst.APP_CONFIG);
let language = appDefaultConfig.language;
if (appConfigStr) {
  try {
    const cached = JSON.parse(appConfigStr);
    if (cached && cached.configVersion === appDefaultConfig.configVersion) {
      state = cached;
      language = state.language;
    } else if (
      cached &&
      typeof cached.configVersion === 'number' &&
      cached.configVersion >= 3 &&
      cached.configVersion < appDefaultConfig.configVersion
    ) {
      state = migrateConfig(cached);
      language = state.language;
    } else {
      // 未知旧结构直接回落默认配置
      state = {...appDefaultConfig};
      language = appDefaultConfig.language;
    }
  } catch (e) {
    smartSentry.captureError(e);
  }
}

/** 取初始化的语言 */
export const getInitializedLanguage = function () {
  return language;
};

export const useAppConfigStore = defineStore({
  id: 'appConfig',
  state: (): AppConfig => ({
    // 本地缓存的配置，没有则用默认值
    ...state,
    // 全屏
    fullScreenFlag: false,
  }),
  actions: {
    reset() {
      this.$patch({...appDefaultConfig});
    },
    showHelpDoc() {
      this.helpDocExpandFlag = true;
    },
    hideHelpDoc() {
      this.helpDocExpandFlag = false;
    },
    startFullScreen() {
      this.fullScreenFlag = true;
    },
    exitFullScreen() {
      this.fullScreenFlag = false;
    },
  },
});
