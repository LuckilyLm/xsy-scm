import vue from 'eslint-plugin-vue';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';

// ESLint 9 flat configuration for the SmartAdmin Vue/TypeScript workspace.
export default [
  {
    ignores: [
      '**/node_modules/**', '**/dist*/**', '**/test-results/**', '**/playwright-report/**',
      '**/*.sh', '**/*.md', '**/*.woff', '**/*.ttf', '**/.vscode/**', '**/.idea/**',
      'lib/**', '**/public/**', '**/docs/**', '**/.husky/**', '**/.local/**', '**/bin/**',
      '**/Dockerfile', 'src/assets/**',
    ],
  },
  ...vue.configs['flat/essential'],
  {
    files: ['src/**/*.vue'],
    languageOptions: { parserOptions: { parser: tsParser, ecmaVersion: 'latest', sourceType: 'module' } },
    rules: { 'vue/multi-word-component-names': ['error', { ignores: ['index'] }], 'vue/no-mutating-props': ['error', { shallowOnly: true }] },
  },
  {
    files: ['src/**/*.ts', 'src/**/*.tsx', 'e2e/**/*.ts', 'playwright.config.ts'],
    languageOptions: { parser: tsParser, parserOptions: { ecmaVersion: 'latest', sourceType: 'module' } },
  },
  {
    files: ['e2e/**/*.ts', 'playwright.config.ts'],
    plugins: {'@typescript-eslint': tsPlugin},
    rules: {
      ...tsPlugin.configs.recommended.rules,
      // Match the existing src lint policy; E2E typing is checked separately by tsc.
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
];
