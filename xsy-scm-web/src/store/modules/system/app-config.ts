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

let state = {
    ...appDefaultConfig
};

let appConfigStr = localRead(localStorageKeyConst.APP_CONFIG);
let language = appDefaultConfig.language;
if (appConfigStr) {
    try {
        const cached = JSON.parse(appConfigStr);
        // 版本迁移只刷新「默认值改过」的项，保留其他用户偏好。
        // 5 → 6：侧栏宽度 208 → 168、主题色改极光绿、标签页位置改顶部、默认隐藏水印与帮助文档。
        if (cached && cached.configVersion === appDefaultConfig.configVersion) {
            state = cached;
            language = state.language;
        } else if (cached
            && (cached.configVersion === 3 || cached.configVersion === 4 || cached.configVersion === 5)
            && appDefaultConfig.configVersion === 6) {
            state = {
                ...appDefaultConfig,
                ...cached,
                configVersion: appDefaultConfig.configVersion,
                sideMenuWidth: appDefaultConfig.sideMenuWidth,
                colorIndex: appDefaultConfig.colorIndex,
                pageTagLocation: appDefaultConfig.pageTagLocation,
                watermarkFlag: appDefaultConfig.watermarkFlag,
                helpDocFlag: appDefaultConfig.helpDocFlag,
            };
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
