/**
 * CSS 文件的替身。
 *
 * Jest 不认识 CSS，直接 import 会报 "Unexpected token ':'"。
 * 样式在组件测试里本来也无意义——测的是结构与文案，不是外观。
 */
module.exports = {};
