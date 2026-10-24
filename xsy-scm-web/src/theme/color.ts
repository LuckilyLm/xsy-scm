export const themeColors = [
  // 蓝色
  {
    primaryColor: '#1677ff',
    activeColor: '#0958d9',
    hoverColor: '#4096ff',
  },
  // 蓝色2
  {
    primaryColor: '#2F54EB',
    activeColor: '#1d39c4',
    hoverColor: '#597ef7',
  },
  // 绿色
  {
    primaryColor: '#00b96b',
    activeColor: '#00945b',
    hoverColor: '#20c77c',
  },

  // 红色
  {
    primaryColor: '#F5222D',
    activeColor: '#cf1322',
    hoverColor: '#ff4d4f',
  },
  // 青色
  {
    primaryColor: '#13c2c2',
    activeColor: '#08979c',
    hoverColor: '#36cfc9',
  },
  // 粉色
  {
    primaryColor: '#EB2F96',
    activeColor: '#c41d7f',
    hoverColor: '#f759ab',
  },
  // 紫色
  {
    primaryColor: '#722ED1',
    activeColor: '#531dab',
    hoverColor: '#9254de',
  },
  // 园圃绿：与客户侧商城同一支绿，白字对比度 5:1
  {
    primaryColor: '#15803D',
    activeColor: '#14532D',
    hoverColor: '#166534',
  },
  // 深绿
  {
    primaryColor: '#009688',
    activeColor: '#007069',
    hoverColor: '#1aa391',
  },
  // 橙色
  {
    primaryColor: '#fa541c',
    activeColor: '#d4380d',
    hoverColor: '#ff7a45',
  },
];

/**
 * 把主色按比例混入底色，生成选中底、浅描边这类派生色。
 *
 * antd 默认算法从深色主色推导 `colorPrimaryBg` 时会得到偏灰的中间色
 * （例如园圃绿会变成 #b4bfb6），菜单选中和选中项像蒙了一层灰。
 * 这里统一按「主色占 amount」直接合成，任何主题色都能得到干净的浅色底。
 */
export function mixColor(color: string, base: string, amount: number): string {
  const parse = (value: string) => {
    const hex = value.replace('#', '');
    const full = hex.length === 3 ? hex.replace(/./g, (ch) => ch + ch) : hex;
    return [0, 2, 4].map((index) => parseInt(full.slice(index, index + 2), 16));
  };
  const [r1, g1, b1] = parse(color);
  const [r2, g2, b2] = parse(base);
  const channel = (from: number, to: number) => Math.round(from * amount + to * (1 - amount));
  return `#${[channel(r1, r2), channel(g1, g2), channel(b1, b2)].map((value) => value.toString(16).padStart(2, '0')).join('')}`;
}
