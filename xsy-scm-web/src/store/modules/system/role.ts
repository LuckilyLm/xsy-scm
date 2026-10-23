/* 角色权限树 */
import _ from 'lodash';
import {defineStore} from 'pinia';

export const useRoleStore = defineStore({
  id: 'role',
  state: () => ({
    checkedData: [],
    treeMap: new Map(),
  }),

  actions: {
    // 初始化权限树选中数据
    initCheckedData(data) {
      this.checkedData = [...new Set(data)];
    },
    // 选中一项
    addCheckedData(data) {
      if (this.checkedData.some((e) => e === data)) {
        return;
      }
      this.checkedData.push(data);
    },
    // 选中本级以及子级
    addCheckedDataAndChildren(data) {
      let findIndex = this.checkedData.findIndex((val) => val === data.menuId);
      if (data.menuId && findIndex === -1) {
        this.addCheckedData(data.menuId);
      }
      if (data.children) {
        data.children.forEach((item) => {
          this.addCheckedDataAndChildren(item);
        });
      }
    },
    // 取消选中
    deleteCheckedData(index) {
      this.checkedData.splice(index, 1);
    },
    // 取消选中本级以及子级
    deleteCheckedDataAndChildren(data) {
      let findIndex = this.checkedData.findIndex((val) => val === data.menuId);
      if (findIndex !== -1) {
        this.deleteCheckedData(findIndex);
      }
      if (data.children) {
        data.children.forEach((item) => {
          this.deleteCheckedDataAndChildren(item);
        });
      }
    },
    // 建立 menuId → 节点的索引
    initTreeMap(tree) {
      for (let treeElement of tree) {
        if (!treeElement.menuId) {
          continue;
        }
        this.treeMap.set(treeElement.menuId, treeElement);
        if (treeElement.children && !_.isEmpty(treeElement.children)) {
          this.initTreeMap(treeElement.children);
        }
      }
    },
    // 选中上一级
    selectUpperLevel(module) {
      let parentId = module.parentId;
      if (!parentId) {
        return;
      }
      let parentModule = this.treeMap.get(parentId);
      if (!parentModule) {
        return;
      }
      let parentIndex = this.checkedData.findIndex((e) => parentModule.menuId === e);
      if (parentModule.menuId && parentIndex === -1) {
        this.addCheckedData(parentModule.menuId);
      }
      // 继续向上选中
      if (parentModule.parentId) {
        this.selectUpperLevel(parentModule);
      }
    },
  },
});
