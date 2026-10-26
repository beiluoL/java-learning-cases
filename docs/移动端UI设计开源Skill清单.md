# 移动端 UI 设计开源 Skill 清单

> 检索时间：2026-08-18 ｜ 面向需求：① 绘制移动端 UI 界面 ② 与 AI 画布相关 ③ 专注移动端 UI 设计 ④ 提升 UI 设计质量
> 说明：下列 skill 多数来自 **Agent Skills 生态**（Claude Code / Codex / Cursor / Windsurf / Gemini CLI 等，以 `SKILL.md` 为单元），部分为独立 CLI / API 工具型 skill。少数已可装在 WorkBuddy；本机已内置的会单独标出。

---

## 一、四类需求覆盖总览

| Skill 名称 | ① 绘制移动端 UI | ② AI 画布/原型 | ③ 专注移动端 | ④ 提升质量 | 形态 |
|---|---|---|---|---|---|
| ui-design-aitool-plus | ✅ | ✅ 可交互 HTML 原型 | ✅ 11 平台 | ✅ 自带 7 维审查 | SKILL |
| mobile-app-design | ✅ | ⚠️ 规范+代码 | ✅ iOS/Android | ✅ 校验脚本 | SKILL |
| mobile-android-design | ✅ | — | ✅ Android/Compose | ✅ MD3 | SKILL |
| sleek-design-mobile-apps | ✅ | ✅ Sleek 画布 | ✅ Mobile | ✅ 真实渲染 | API Skill |
| ui-mobile | ✅ | — | ✅ RN/iOS/Android | ✅ 触控目标 | SKILL |
| mobile-design | ✅ | — | ✅ 移动优先 | ✅ 可行性评估 | SKILL |
| easy-wireframe | ✅ | ✅ 线框画布 | ✅ 移动端+PC | ⚠️ 结构级 | SKILL |
| cc-design | ⚠️ 高保真 | ✅ design canvas | ⚠️ 通用 | ✅ 三阶段验证 | SKILL |
| html-design-prototypes | ⚠️ 原型 | ✅ 浏览器画布 | ⚠️ 通用 | ✅ 设计探索 | SKILL |
| AGenUI / A2UI | ✅ | ✅ 端侧原生画布 | ✅ 移动端 | ✅ 原厂规范 | 协议+Skill |
| ui-ux-master | ⚠️ 通用 | — | ⚠️ 含移动端 | ✅ 设计系统 | SKILL |
| ui-ux-pro-max | ⚠️ 通用 | — | ✅ 含 RN/Flutter | ✅ 大数据库 | SKILL |
| UI/UX 设计顾问 | ⚠️ 评审 | — | ✅ 含移动端 | ✅ 评审反馈 | SKILL |
| taste-skill | ⚠️ 通用 | ⚠️ 图像生成 | ✅ 含 mobile 变体 | ✅ 反 slop | SKILL 套件 |

---

## 二、分类清单（名称 / 简介 / 适用平台 / 获取方式）

### A. 专注移动端 UI 设计（绘制 + 规范 + 原型）

**1. ui-design-aitool-plus**
- 简介：号称"一人设计公司"。把 11 大平台官方设计规范（Apple iOS HIG、Google Material Design 3、Samsung One UI、华为 HarmonyOS、小米 HyperOS、荣耀 MagicOS、OPPO ColorOS、vivo OriginOS、魅族 Flyme、Windows 11 Fluent、微信小程序 WeUI）编码进知识库；v2.0 起内置 100 家大厂品牌风格（腾讯/阿里/字节/Stripe/Vercel/Spotify 等）。内部跑 8 步工作流：需求分析 → 风格确认 → 设计系统生成 → 组件规范 → 页面原型 → 审查迭代 → 交付 → Demo。默认输出**可交互 HTML 原型（双主题+响应式）**，也可一键导出 Figma / MasterGo / 即时设计 / 墨刀 / 蓝湖 / Pixso。
- 适用平台/框架：iOS / Android / 鸿蒙 / 微信小程序 / 桌面；输出 HTML、Figma、MasterGo、Pixso 等。
- 获取方式：
  - GitHub：`git clone https://github.com/aitool-plus/ui-design-aitool-plus.git ~/.claude/skills/ui-design-aitool-plus`
  - 腾讯 SkillHub：`https://skillhub.cn/skills/ui-design-aitool-plus`（按 `https://skillhub.cn/install/skillhub.md` 安装，技能 ID `@user_76c74409/ui-design-aitool-plus`）

**2. mobile-app-design**（awesome-skills）
- 简介：综合移动端 UI/UX 设计指南 skill，把 Claude 训练成移动端设计专家。覆盖 iOS HIG、Android Material Design、WCAG 2.1 AA 无障碍、React Native 性能优化、iOS vs Android 差异对比。附带**校验脚本**：对比度检查（check-contrast.py）、触控目标校验（validate-touch-targets.sh）、无障碍审计（accessibility-audit.sh）。
- 适用平台/框架：iOS / Android / React Native。
- 获取方式：`git clone https://github.com/awesome-skills/mobile-app-design.git ~/.claude/skills/mobile-app-design`

**3. mobile-android-design**（wshobson）
- 简介：专注掌握 Material Design 3 与 Jetpack Compose 模式，用于原生 Android 应用设计。含组件示例（Compose 卡片、导航、动态配色、自适应布局）。
- 适用平台/框架：Android / Jetpack Compose。
- 获取方式：`npx skillhub install wshobson/agents/mobile-android-design`

**4. sleek-design-mobile-apps**（sleekdotdesign）
- 简介：通过对 Sleek（sleek.design）REST API 让 AI 设计移动 App 界面并渲染成真实屏幕图；可在代码侧落地为 HTML / React Native / SwiftUI。需 `SLEEK_API_KEY`（有免费试用额度，Pro $49.99/月）。
- 适用平台/框架：Sleek 画布 → HTML / React Native / SwiftUI。
- 获取方式：`npx bzskills add sleekdotdesign/agent-skills --skill sleek-design-mobile-apps`

**5. ui-mobile**（alinaqi / maggy 仓库）
- 简介：聚焦 React Native UI 模式、iOS/Android 设计约定、触控目标尺寸、移动优先组件、TypeScript 实现建议。适合在 React Native 项目里做平台感知的 UI 指导。
- 适用平台/框架：React Native / iOS / Android。
- 获取方式：`git clone https://github.com/alinaqi/maggy.git`（在其 skills 目录中定位 `ui-mobile` 引入）

**6. mobile-design**（slurpyb-registry，LobeHub 市场）
- 简介：移动优先（touch-first）设计工程原则，强调"移动不是小号桌面"。含移动可行性风险指数 MFRI、触控/手势设计、自适应布局、离线优先、发布就绪清单。覆盖 React Native / Flutter / 原生。
- 适用平台/框架：React Native / Flutter / SwiftUI / Compose。
- 获取方式：LobeHub Skills Marketplace（`npx -y @lobehub/market-cli skills add slurpyb-registry-mobile-design`）

**7. easy-wireframe**（teo5777）
- 简介：生成移动端 App 原型 / 页面流程线框图的通用 skill，输出**零依赖单文件 HTML**。移动端用 iPhone 375×812 尺寸 + Apple HIG 字号，网格平铺 + 连线标注"点哪 → 去哪页"。适合快速画页面流转，不纠结视觉细节。
- 适用平台/框架：移动端 App（375×812）+ PC 后台（1440×900）；输出纯 HTML。
- 获取方式：`npx skills add https://github.com/teo5777/easy-wireframe --skill easy-wireframe`

---

### B. 与 AI 画布 / 原型画布相关（满足"AI canvas"需求）

**8. cc-design**（zorojean / ZeroZ-lab）
- 简介：高保真 HTML 设计与原型 skill，把"专家产品设计师"工作流嵌进 Agent。内置 **design canvas（设计画布）**：`ios_frame.jsx` / `android_frame.jsx` 设备框、`design_canvas.jsx` 选项网格、`tweaks-system` 页内实时调参；支持 68+ 品牌设计系统克隆、三阶段验证（结构/视觉/设计卓越）、导出 PPTX/PDF/单文件 HTML。
- 适用平台/框架：生成 HTML 原型（含 iOS/Android 设备框），可对接 React/Vue 等；用 Playwright MCP 做截图验证。
- 获取方式：`git clone https://github.com/zorojean/cc-design.git ~/.claude/skills/cc-design`（需 `cd scripts && npm install && npx playwright install chromium`）

**9. html-design-prototypes**（f-labs-io / html-skills）
- 简介：把设计思考画在浏览器画布上的 skill——为任意 UI 元素（组件/动画/布局/设计系统）生成**真实 .html 文件**并打开预览，而非内联渲染。适合在动手写生产代码前做设计探索，即使目标框架是 React Native / Swift 也先用 HTML 快速试错。
- 适用平台/框架：HTML/CSS 画布（设计探索面），产出可转译到 RN/Swift/Android。
- 获取方式：`npx claudepluginhub f-labs-io/agent-html-skills --plugin html-skills`

**10. AGenUI / A2UI Generation Skill**（华为开源）
- 简介：基于 Google A2UI 协议，把 Agent 输出直接渲染为**可交互原生卡片**，跑在 iOS / Android / HarmonyOS 上。附带的 A2UI 协议生成 Skill 可挂载到任意支持 Agent Skills 机制的 Agent（Claude Code / Codex / Qoder 等），让 LLM 依据内置设计规范把自然语言转成 A2UI 协议消息（updateComponents / updateDataModel）。这是最贴近"移动端 AI 画布"的端侧原生方案。
- 适用平台/框架：iOS / Android / HarmonyOS 原生渲染；Agent Skills 机制。
- 获取方式：华为已开源，GitHub 检索 `AGenUI`（仓库内 `skills/a2ui-generation/` 目录）；介绍见华为开发者博客 `https://developer.huawei.com/consumer/cn/blog/topic/03214225321112018`

> 参考（非 skill，但属"AI 画布"产品）：**Claude Design**（Anthropic 原生创意设计工具，2 个 prompt 出原型并导出 Canva/PDF/PPTX/HTML）、**Gemini Canvas**（把草图/截图转成可交互 Web App，实时预览）。可作为上面 skill 的思路参照。

---

### C. 通用 UI 设计质量提升（含移动端，满足"提升质量"需求）

**11. ui-ux-master**（openclaw）
- 简介：结合 Apple HIG、现代 Web 设计、SuperDesign 模式、通用设计原则，覆盖 iOS/macOS/Web、设计系统、无障碍、响应式、动画。14 个模块化参考文件。
- 适用平台/框架：iOS / macOS / Web / 全平台。
- 获取方式：`npx skillhub install openclaw/skills/ui-ux-master`

**12. ui-ux-pro-max**（nextlevelbuilder）
- 简介：UI/UX 设计情报库，84 风格、192 配色、74 字体组合、98 条 UX 指南、16 个 GSAP 动效预设、25 种图表，**覆盖 22 个技术栈**（含 SwiftUI / React Native / Flutter / Jetpack Compose / Tailwind / shadcn 等）。含本地可搜索数据库与优先级规则。
- 适用平台/框架：React / Next / Vue / Svelte / SwiftUI / RN / Flutter / Compose 等 22 栈。
- 获取方式：`npx bzskills add nextlevelbuilder/ui-ux-pro-max-skill --skill ui-ux-pro-max`

**13. UI/UX 设计顾问**（腾讯 SkillHub，科恩/云鼎实验室）
- 简介：评审界面设计、给改进建议、生成设计规范与 UX 优化方案。适用"帮我评审这个移动端页面""这个交互是否合理"等。已通过安全扫描。
- 适用平台/框架：移动端 / Web 端 UI 评审。
- 获取方式：`https://skillhub.cloud.tencent.com/skills/ui-design-zh`（按 skillhub.md 安装 `@user_7871dce1/ui-design-zh`）

**14. taste-skill**（Leonxlnx）
- 简介：带 3 个可调参数（DESIGN_VARIANCE / MOTION_INTENSITY / VISUAL_DENSITY，各 1-10）的高端设计套件，含 9 个专业变体 + 3 个图像生成 skill。其中 **imagegen-frontend-mobile** 专门生成 iOS/Android/跨平台移动 App 屏幕概念图（带手机 mockup 取景），适合先出概念再落地。
- 适用平台/框架：框架无关；含移动端图像生成变体。
- 获取方式：`npx skills add https://github.com/Leonxlnx/taste-skill`

---

### D. 本机 WorkBuddy 已内置的相关 skill（加分项，无需外装）

- **mobile-ui-design-spec**：基于 Apple HIG + Material Design 3 的通用移动端 UI 设计规范，覆盖输入框、字体排印、列表/卡片、配色与深色模式。→ 直接满足 ③④，适合出规范初稿。
- **ui-design-system**：Ant Design 5 Design Token 体系（主色 #1677ff、4px 间距基数、状态五态、WCAG AA）。→ 满足 ④，中后台/移动端混合界面可用。

> 这两个已随本机环境提供，可在对话中直接调用，不需要任何安装步骤。

---

## 三、它们如何提升移动端 UI 设计效率与质量

1. **消除"缝合怪"，保证规范一致性**：ui-design-aitool-plus、mobile-app-design 等把官方规范（HIG / MD3）编码进知识库，参数有出处（4pt 网格、SF 字体、圆角/间距/动效曲线），不再靠 AI 凭感觉凑，前端可像素级落地。
2. **把设计决策前置、可复用**：ui-ux-pro-max、ui-ux-master 用"设计系统 + 调色板 + 字体组合"数据库，一句话就能给出与行业匹配的风格，避免每次从零调 CSS。
3. **可视化画布快速试错**：cc-design、html-design-prototypes、easy-wireframe 把界面画成可交互 HTML/画布，先验证布局与流转再写生产代码，沟通成本大幅下降。
4. **质量自检闭环**：ui-design-aitool-plus 的"7 维还原度打分"、mobile-app-design 的对比度/触控目标/无障碍脚本，让 AI 自己审查自己，把"从 0 到 60 分"的及格线拉到专业级。
5. **端侧原生渲染新范式**：AGenUI/A2UI 让自然语言直接变成 iOS/Android/鸿蒙 原生卡片，是"移动端 AI 画布"的前沿方向。
6. **本机即开即用**：WorkBuddy 已自带 mobile-ui-design-spec / ui-design-system，规范级初稿零安装可得。

---

## 四、安装方式速查

```bash
# 通用 Agent Skills 目录（Claude Code 等）
git clone <repo-url> ~/.claude/skills/<skill-name>

# SkillHub CLI
npx skillhub install wshobson/agents/mobile-android-design
npx skillhub install openclaw/skills/ui-ux-master

# bzskills（腾讯系市场）
npx bzskills add nextlevelbuilder/ui-ux-pro-max-skill --skill ui-ux-pro-max
npx bzskills add sleekdotdesign/agent-skills --skill sleek-design-mobile-apps

# skills CLI
npx skills add https://github.com/teo5777/easy-wireframe --skill easy-wireframe
npx skills add https://github.com/Leonxlnx/taste-skill

# 本机已内置（无需安装）
mobile-ui-design-spec  /  ui-design-system
```

> 提示：以上 skill 多为 `SKILL.md` 格式，主要面向 Claude Code / Codex / Cursor 等 Agent 环境。在 WorkBuddy 中可直接使用内置的 `mobile-ui-design-spec` 与 `ui-design-system`；外部 skill 若有需要，可手动落为 `~/.workbuddy/skills/` 下的 SKILL.md 后加载。
