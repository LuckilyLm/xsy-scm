/*
 * ESLint 扁平配置（flat config）
 *
 * ESLint 9 起 .eslintrc.* 已不再被读取，必须使用 eslint.config.js。
 * 规则集与参考项目 xsy-app 的 .eslintrc.cjs 保持一致，只是换了承载形式，
 * 避免升级 lint 工具链时顺手改变代码风格要求。
 */
import js from '@eslint/js';
import globals from 'globals';
import pluginVue from 'eslint-plugin-vue';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';

export default [
  {
    ignores: ['node_modules/**', 'dist/**', 'unpackage/**', 'src/uni_modules/**', 'src/static/**', '**/*.min.js'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  eslintPluginPrettierRecommended,
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        // uni-app 运行时全局对象
        uni: 'readonly',
        wx: 'readonly',
        plus: 'readonly',
        getApp: 'readonly',
        getCurrentPages: 'readonly',
        // Vue 单文件组件编译宏
        defineProps: 'readonly',
        defineEmits: 'readonly',
        defineExpose: 'readonly',
        defineOptions: 'readonly',
        withDefaults: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': [
        'error',
        // 仅用于捕获未使用的参数之外的情况；变量本身由其它规则覆盖
        { varsIgnorePattern: '.*', args: 'none' },
      ],
      'space-before-function-paren': 'off',

      'vue/attributes-order': 'off',
      'vue/one-component-per-file': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/require-default-prop': 'off',
      'vue/multi-word-component-names': 'error',
      'vue/html-self-closing': [
        'error',
        {
          html: { void: 'always', normal: 'never', component: 'always' },
          svg: 'always',
          math: 'always',
        },
      ],
    },
  },
  {
    /*
     * CommonJS 配置文件（.prettierrc.cjs 等）需要 node 全局变量，
     * 否则 module / require 会被 no-undef 误报。
     */
    files: ['**/*.cjs'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: { ...globals.node },
    },
  },
  {
    /*
     * mock 契约层的 Node 侧脚本（冒烟测试 + 运行器）需要 node 全局变量。
     * 它们在 src/ 之外，也不参与小程序打包，走的是 Node 而不是小程序运行时。
     */
    files: ['mock/**/*.mjs'],
    languageOptions: {
      sourceType: 'module',
      globals: { ...globals.node },
    },
  },
  {
    /*
     * 页面文件豁免「组件名必须多词」。
     *
     * 该规则的作用是避免组件名与 HTML 原生标签冲突，而 uni-app 页面由
     * pages.json 路由驱动，永远不会以组件形式被引用；login / detail /
     * list / search 这类单词命名是页面目录的既有约定。
     *
     * src/components 下的可复用组件不受此豁免，仍需多词命名。
     */
    files: ['src/pages/**/*.vue', 'src/pages-sub/**/*.vue'],
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
];
