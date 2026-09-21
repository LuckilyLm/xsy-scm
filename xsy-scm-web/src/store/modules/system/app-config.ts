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
        // 缓存配置版本低于当前默认配置版本时，说明 app-config.ts 的默认值已更新，
        // 直接采用新默认值（老用户会平滑升级到新配置，而不是一直停留在旧配置上）。
        if (cached && cached.configVersion === appDefaultConfig.configVersion) {
            state = cached;
            language = state.language;
        } else {
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
