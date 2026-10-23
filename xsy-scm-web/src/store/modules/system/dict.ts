import {defineStore} from 'pinia';
import {DICT_SPLIT} from '/@/constants/support/dict-const';
import _ from 'lodash';
import {dictApi} from '/@/api/support/dict-api';
import {smartSentry} from '/@/lib/smart-sentry';

export const useDictStore = defineStore({
  id: 'dict',
  state: () => ({
    // 字典编码集合
    dictList: [],
    // 字典数据
    dictMap: new Map(),
  }),

  actions: {
    getDictList() {
      return this.dictList;
    },
    // 获取字典数据
    getDictData(dictCode) {
      if (!dictCode) {
        return [];
      }
      let dictDataList = this.dictMap.get(dictCode);
      return dictDataList ? dictDataList : [];
    },

    // 按编码取字典项
    getDataList(dictCode, dataValue) {
      if (_.isNil(dataValue) || _.isNaN(dataValue)) {
        return '';
      }

      let dict = this.getDictData(dictCode);
      if (dict.length === 0) {
        return '';
      }

      // 数字先转字符串再匹配
      if (_.isNumber(dataValue)) {
        let target = _.find(dict, {dataValue: String(dataValue)});
        return target ? target.dataLabel : '';
      }

      let valueArray = dataValue.split(DICT_SPLIT);
      let result = [];
      for (let item of valueArray) {
        let target = _.find(dict, {dataValue: item});
        if (target) {
          result.push(target);
        }
      }
      return result;
    },

    // 按编码取字典项的显示名
    getDataLabels(dictCode, dataValue) {
      if (_.isNil(dataValue) || _.isNaN(dataValue)) {
        return '';
      }

      let dict = this.getDictData(dictCode);
      if (dict.length === 0) {
        return '';
      }

      // 数字先转字符串再匹配
      if (_.isNumber(dataValue)) {
        let target = _.find(dict, {dataValue: String(dataValue)});
        return target ? target.dataLabel : '';
      }

      let valueArray = dataValue.split(DICT_SPLIT);
      let result = [];
      for (let item of valueArray) {
        let target = _.find(dict, {dataValue: item});
        if (target) {
          result.push(target.dataLabel);
        }
      }
      return result.join(DICT_SPLIT);
    },
    // 刷新字典
    async refreshData() {
      try {
        const dictRes = await dictApi.getAllDictData();
        this.initData(dictRes.data);
      } catch (e) {
        smartSentry.captureError(e);
      }
    },
    // 初始化字典
    initData(dictDataList) {
      this.dictMap.clear();
      this.dictList = [];

      for (let data of dictDataList) {
        if (!_.some(this.dictList, {dictCode: data.dictCode})) {
          this.dictList.push({
            dictCode: data.dictCode,
            dictName: data.dictName,
            disabledFlag: data.dictDisabledFlag,
          });
        }

        let dataArray = this.dictMap.get(data.dictCode);
        if (!dataArray) {
          dataArray = [];
          this.dictMap.set(data.dictCode, dataArray);
        }
        dataArray.push(data);
      }
    },
  },
});
