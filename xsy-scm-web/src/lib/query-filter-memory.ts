/*
 * 列表页查询条件的「按用户 + 页面」本地记忆（Wave 8 §12.2）。
 *
 * 复用仓库既有偏好模式（订单草稿、导出列偏好）：只存浏览器本地、以 employeeId 隔离，
 * 不建 user_preference 表、不把筛选值写进业务表。owner 缺失（未登录 / 未水合）时全部空操作，
 * 既不会误存到匿名键，也不会跨用户串味。深链详情类页面不应调用本组合式。
 */
import {localRead, localRemove, localSave} from '/@/utils/local-util';
import {useUserStore} from '/@/store/modules/system/user';
import {queryFilterStorageKey} from './query-filter-key';

export function useQueryFilterMemory<T extends object>(pageKey: string) {
  const userStore = useUserStore();

  // 每次调用现取 employeeId（main.ts 在挂载前已 await 登录信息），避免快照过期。
  function owner(): string {
    const id = userStore.employeeId;
    return id === '' || id === null || id === undefined ? '' : String(id);
  }

  function load(): Partial<T> {
    const key = owner();
    if (!key) {
      return {};
    }
    try {
      const raw = localRead(queryFilterStorageKey(key, pageKey));
      if (!raw) {
        return {};
      }
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === 'object' ? (parsed as Partial<T>) : {};
    } catch {
      // 偏好损坏静默回落默认条件——记忆只是便利，不能挡住查询。
      return {};
    }
  }

  function save(filters: T): void {
    const key = owner();
    if (!key) {
      return;
    }
    localSave(queryFilterStorageKey(key, pageKey), JSON.stringify(filters));
  }

  function clear(): void {
    const key = owner();
    if (!key) {
      return;
    }
    localRemove(queryFilterStorageKey(key, pageKey));
  }

  return {load, save, clear};
}
