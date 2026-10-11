/** @type {import('jest').Config} */
module.exports = {
  // jest-expo 的 preset 处理了 Expo / React Native 那些必须被转换的 ESM 依赖
  // （transformIgnorePatterns 里一长串包名）。手写这份配置很难维护对。
  preset: 'jest-expo',

  testMatch: ['<rootDir>/src/**/*.test.ts', '<rootDir>/src/**/*.test.tsx'],

  // 顺序有意义：Jest 按声明顺序匹配，CSS 规则必须排在 "@/" 之前。
  // 否则 "@/global.css" 会先被别名规则改写成 src/global.css（文件真实存在），
  // 然后 Jest 去解析它的内容，报一个和配置毫无关系的语法错误。
  moduleNameMapper: {
    '\\.css$': '<rootDir>/jest/style-mock.js',
    '^@/(.*)$': '<rootDir>/src/$1',
  },

  collectCoverageFrom: ['src/lib/**/*.ts'],
};

// ---------------------------------------------------------------------------
// 关于组件测试（@testing-library/react-native）
//
// 当前**没有**组件测试。不是遗漏，是试过之后放弃的，记录一下省得重复踩：
//
// 症状：所有 render() 调用都失败，报 "overlapping act() calls"，
// 紧接着是 "render function has not been called"——后者看着像是 render 没执行，
// 实际是它抛异常了。而这两条错误都完全指不到真正的原因。
//
// 试过并排除的：
//   - React 副本重复。这个 monorepo 里确实有三份 React（根目录 19.3.0 给 web、
//     apps/mobile 的 19.2.3 给 Expo SDK 57），RNTL 被提升到根目录后会拿到错的那份。
//     用 moduleNameMapper 强制解析到本 workspace 后，路径确实变了，但错误依旧。
//   - CSS 解析失败。已用 style-mock 排除，且它是另一条独立的报错。
//   - 降级 RNTL 到 13.x。被 npm 的 peer 依赖冲突挡住（ERESOLVE），装不上。
//
// 真正的原因大概率是版本生态不匹配：RNTL 14.1.0 依赖 jest-matcher-utils@^30
// 与 pretty-format@^30，也就是 **Jest 30**；而 jest-expo 57（Expo SDK 57 对应版本）
// 是围绕 Jest 29 构建的。强行升级 Jest 会连带影响 jest-expo 依赖的
// jest-environment-jsdom 等组件，风险大于收益。
//
// 界面行为的覆盖目前由 apps/web 的 Playwright 端到端测试承担（12 个用例，
// 覆盖注册登录、发布活动、报名、候补递补、票券）。
// 等 Expo SDK 升级到配套 Jest 30 的版本后，可以再回来补组件测试。
// ---------------------------------------------------------------------------
