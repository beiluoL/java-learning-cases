import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import vue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import globals from 'globals'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', '**/*.d.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/essential'],
  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser },
    },
    rules: {
      // TS / Vue 的宏与编译产物由编译器负责，no-undef 交给类型系统。
      'no-undef': 'off',
      'vue/multi-word-component-names': 'off',
      // 升级为 error：存量已 0 违规，禁止回退。
      // `lint` 脚本带 --max-warnings=0，因此任何 warning 也会阻断门禁。
      '@typescript-eslint/no-unused-vars': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      // 禁止遗留调试输出
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      // 禁止 Options API，统一 <script setup>
      'vue/component-api-style': ['error', ['script-setup']],
      // props 必须类型化
      'vue/define-props-declaration': ['error', 'type-based'],
      // v-for 必须有 key
      'vue/require-v-for-key': 'error',
    },
  },
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser, ecmaVersion: 2022, sourceType: 'module' },
    },
  },
  {
    // 测试文件允许直接使用 describe/it/expect
    files: ['**/*.spec.ts'],
    languageOptions: { globals: { ...globals.node } },
  },
  // 必须放最后：关闭与 Prettier 冲突的格式类规则，避免两套工具互相打架
  prettier,
)
