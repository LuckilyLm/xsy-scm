import type { ThemeConfig } from 'antd';
import { adminTokens } from './tokens';

export const adminTheme: ThemeConfig = {
  token: {
    colorPrimary: adminTokens.colorPrimary,
    colorInfo: adminTokens.colorPrimary,
    colorBgLayout: adminTokens.colorBgLayout,
    colorBgContainer: adminTokens.colorBgContainer,
    colorText: adminTokens.colorText,
    colorTextSecondary: adminTokens.colorTextSecondary,
    colorBorder: adminTokens.colorBorder,
    borderRadius: 2,
    controlHeight: 32,
    fontSize: 14,
  },
  components: {
    Button: {
      borderRadius: 2,
      controlHeight: 32,
      primaryShadow: 'none',
    },
    Table: {
      headerBg: '#F2F3F5',
      headerColor: adminTokens.colorText,
      rowHoverBg: '#F2FBF7',
      borderColor: adminTokens.colorBorder,
      cellPaddingBlock: 10,
      cellPaddingInline: 12,
    },
    Tabs: {
      itemActiveColor: adminTokens.colorPrimaryActive,
      itemSelectedColor: adminTokens.colorPrimary,
      inkBarColor: adminTokens.colorPrimary,
    },
  },
};
