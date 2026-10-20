/** 仅已配置的正式生产域名启用统计；本机秤端与演练永远不加载远程脚本。 */
export function initializeAnalytics(): void {
  const {hostname} = location;
  const domain = import.meta.env.VITE_PRODUCTION_DOMAIN;
  const website = import.meta.env.VITE_UMAMI_WEBSITE_ID;
  const source = import.meta.env.VITE_UMAMI_SCRIPT_URL;
  if (location.protocol !== 'https:' || !import.meta.env.PROD || !domain || !website || !source || hostname !== domain
    || hostname === 'localhost' || hostname === '::1' || hostname === '[::1]' || /^127\./.test(hostname)) return;
  const script = document.createElement('script'); script.defer = true;
  script.src = source; script.dataset.websiteId = website; document.head.append(script);
}
