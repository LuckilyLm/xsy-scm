// Minimal Vite config for Knip: it only needs the `/@/` alias and Vue SFC parsing.
// The real config imports ant-design-vue's CJS theme helper, which Knip's config
// loader cannot interop with.
import {resolve} from 'node:path';
import vue from '@vitejs/plugin-vue';

export default {
  resolve: {
    alias: [{find: /\/@\//, replacement: `${resolve(__dirname, 'src')}/`}],
  },
  plugins: [vue()],
};
