/**
 * Stylelint 配置（CSS / Vue scoped 样式）
 *
 * 分级策略（符合 CODE_STANDARDS.md「渐进式治理」）：
 *   - error   ：必须修，否则门禁不通过
 *   - warning ：先暴露出来，B8 批次收敛后再升级为 error
 *
 * 当前唯一 warning 是「硬编码色值」：存量分布在 10 个视图的 scoped 样式里
 * （PROJECT_CODE_AUDIT.md FE-05），一次性全改属于大规模样式迁移，
 * 与本轮「不改变视觉表现」的约束冲突，因此先收集清单，再分批替换为设计令牌。
 */
export default {
  extends: ['stylelint-config-standard', 'stylelint-config-recommended-vue'],
  ignoreFiles: ['dist/**', 'node_modules/**'],
  rules: {
    // ---- 结构性问题：error ----
    'block-no-empty': true,
    'color-no-invalid-hex': true,
    'declaration-block-no-duplicate-properties': [true, { ignore: ['consecutive-duplicates'] }],
    'no-duplicate-selectors': true,
    'selector-pseudo-element-no-unknown': [true, { ignorePseudoElements: ['v-deep', 'v-global', 'v-slotted'] }],
    'property-no-unknown': [true, { ignoreProperties: ['composes'] }],

    // ---- 与项目既有风格冲突的规则：显式关闭并写明原因 ----
    // 项目用 kebab-case 的 BEM 命名，但不强制 block__element--modifier 格式
    'selector-class-pattern': null,
    // 设计令牌集中定义，不要求空行分组
    'custom-property-empty-line-before': null,
    // `border: 1px solid var(--x)` 比 4 条 longhand 更清晰
    'declaration-block-no-redundant-longhand-properties': null,
    // 视图样式按页面组织，选择器顺序不强制
    'no-descending-specificity': null,
    // 允许 :deep() 穿透（Vue scoped 必需）
    'selector-pseudo-class-no-unknown': [true, { ignorePseudoClasses: ['deep', 'global', 'slotted'] }],
    // 注释格式自由
    'comment-empty-line-before': null,
    'rule-empty-line-before': null,
    'at-rule-empty-line-before': null,
    'declaration-empty-line-before': null,

    // ---- 纯书写风格类规则：关闭，避免无意义的 CSS 大范围改动 ----
    // 这些规则只影响"怎么写"，不影响渲染结果。开启它们会产生数十处 CSS 改写，
    // 与「不改变视觉表现、最小 diff」的约束冲突，且收益为零。
    'color-function-notation': null,
    'color-function-alias-notation': null,
    'alpha-value-notation': null,
    'media-feature-range-notation': null,
    'color-hex-length': null,
    'shorthand-property-no-redundant-values': null,
    'declaration-property-value-keyword-no-deprecated': null,

    // ---- 硬编码色值：先 warning，收敛后改 error ----
    'declaration-property-value-disallowed-list': [
      {
        '/^(color|background|background-color|border-color|outline-color|fill|stroke)$/': [
          '/^#[0-9a-fA-F]{3,8}$/',
        ],
      },
      { severity: 'warning', message: '请改用 styles/tokens.css 中的设计令牌，如 var(--color-primary)' },
    ],
  },
}
