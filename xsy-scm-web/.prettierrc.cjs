/*
 * 前端代码格式化配置。
 *
 * 与根目录 .editorconfig 保持一致：2 空格缩进、LF 换行、单引号、行宽 150。
 * 仓库统一以 Prettier 为唯一格式化入口，ESLint 不重复承担排版规则。
 */
module.exports = {
  printWidth: 150,
  tabWidth: 2,
  useTabs: false,
  semi: true,
  singleQuote: true,
  vueIndentScriptAndStyle: false,
  quoteProps: 'as-needed',
  jsxSingleQuote: true,
  trailingComma: 'es5',
  bracketSpacing: false,
  bracketSameLine: false,
  arrowParens: 'always',
  proseWrap: 'preserve',
  htmlWhitespaceSensitivity: 'css',
  endOfLine: 'lf',
};
