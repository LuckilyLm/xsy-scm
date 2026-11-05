/** 更换配置名称时保留本机登录和偏好；已有新配置优先，写入失败时保留旧值。 */
export function upgradeStoredSettings(storage: Storage | undefined = typeof localStorage === 'undefined' ? undefined : localStorage): void {
  if (!storage) return;
  const previousPrefix = 'smart_admin_';
  try {
    const keys = Array.from({length: storage.length}, (_, index) => storage.key(index)).filter((key): key is string => !!key);
    for (const key of keys) {
      if (!key.startsWith(previousPrefix)) continue;
      const currentKey = `xsy_${key.slice(previousPrefix.length)}`;
      const value = storage.getItem(key);
      if (storage.getItem(currentKey) === null && value !== null) storage.setItem(currentKey, value);
      // 只有新值确实可读时才移除旧键，不能因浏览器容量或访问限制丢失原配置。
      if (storage.getItem(currentKey) !== null) storage.removeItem(key);
    }
  } catch {
    // 浏览器禁止存储时不阻止页面初始化，原配置不做额外清理。
  }
}
