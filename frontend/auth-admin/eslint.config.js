import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

export default tseslint.config({ ignores: ['dist/**', 'node_modules/**'] }, js.configs.recommended, ...tseslint.configs.recommended, ...pluginVue.configs['flat/recommended'], { files: ['**/*.{ts,vue}'], languageOptions: { globals: { ...globals.browser, ...globals.node }, parserOptions: { parser: tseslint.parser } }, rules: { 'no-console': 'error', 'no-debugger': 'error', 'vue/multi-word-component-names': 'off' } }, prettier)
