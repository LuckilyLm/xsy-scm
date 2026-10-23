/* 错误上报 */

export const smartSentry = {
  /** 主动上报错误 */
  captureError: (error) => {
    if (error.config && error.data && error && error.headers && error.request && error.status) {
      return;
    }
    // Sentry.captureException(error);
    console.error(error);
  },
};
