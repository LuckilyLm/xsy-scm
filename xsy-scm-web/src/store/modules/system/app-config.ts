/*
 * 项目的配置信息
 *
 */
import {defineStore} from 'pinia';
import {appDefaultConfig} from '/@/config/app-config';
import localStorageKeyConst from '/@/constants/local-storage-key-const';
import {smartSentry} from '/@/lib/smart-sentry';
import {AppConfig} from '/@/types/config';
import {localRead} from '/@/utils/local-util';

/**
 * 每个版本「默认值改过」的项。迁移时只把这些键拉回新默认，其余键保留用户当前值 ——
 * 一次只重置改过的那几项，用户自己调的宽度、主题、标签页位置不会被顺手抹掉。
 */
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
    // 版本迁移只刷新「默认值改过」的项，保留其他用户偏好。
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
      // 其他版本沿用默认配置，避免把未知旧结构当作当前结构继续使用。
      state = {...appDefaultConfig};
      language = appDefaultConfig.language;
    }
  } catch (e) {
    smartSentry.captureError(e);
  }
}

/**
 * 获取初始化的语言
 */
export const getInitializedLanguage = function () {
  return language;
};

export const useAppConfigStore = defineStore({
  id: 'appConfig',
  state: (): AppConfig => ({
    // 读取config下的默认配置
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
