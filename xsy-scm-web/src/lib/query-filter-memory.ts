/* 列表页查询条件按用户和页面存在浏览器本地；未登录时不做任何存取。 */
import {localRead, localRemove, localSave} from '/@/utils/local-util';
import {useUserStore} from '/@/store/modules/system/user';
import {queryFilterStorageKey} from './query-filter-key';

export function useQueryFilterMemory<T extends object>(pageKey: string) {
  const userStore = useUserStore();

  // 每次现取 employeeId，避免拿到过期的值。
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
      // 偏好损坏时回落默认条件
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
