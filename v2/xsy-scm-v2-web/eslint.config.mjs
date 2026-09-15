import vue from 'eslint-plugin-vue';
import tsParser from '@typescript-eslint/parser';

// ESLint 9 flat configuration for the SmartAdmin Vue/TypeScript workspace.
export default [
  { ignores: ['node_modules/**', 'dist*/**', 'test-results/**', 'playwright-report/**'] },
  ...vue.configs['flat/essential'],
  {
    files: ['src/**/*.vue'],
    languageOptions: { parserOptions: { parser: tsParser, ecmaVersion: 'latest', sourceType: 'module' } },
    rules: { 'vue/multi-word-component-names': ['error', { ignores: ['index'] }], 'vue/no-mutating-props': ['error', { shallowOnly: true }] },
  },
  { files: ['src/**/*.ts', 'src/**/*.tsx'], languageOptions: { parser: tsParser } },
];
