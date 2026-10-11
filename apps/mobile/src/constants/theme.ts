import '@/global.css';

import { Platform } from 'react-native';

/**
 * 配色与 Web 端保持一致（slate 色阶）。
 *
 * 刻意只做浅色：深色模式不是「把背景和文字对调」那么简单——徽章、表单、
 * 卡片边框都得各有一套取值。半套深色模式比没有更糟，所以先统一浅色。
 */
export const Colors = {
  background: '#F8FAFC', // slate-50
  surface: '#FFFFFF',
  border: '#E2E8F0', // slate-200
  borderStrong: '#CBD5E1', // slate-300
  text: '#0F172A', // slate-900
  textMuted: '#64748B', // slate-500
  textFaint: '#94A3B8', // slate-400
  primary: '#0F172A',
  onPrimary: '#FFFFFF',
  success: '#047857', // emerald-700
  successBg: '#ECFDF5',
  warning: '#B45309', // amber-700
  warningBg: '#FFFBEB',
  danger: '#B91C1C', // red-700
  dangerBg: '#FEF2F2',
  info: '#0369A1', // sky-700
  infoBg: '#F0F9FF',
} as const;

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const Radius = {
  sm: 6,
  md: 8,
  lg: 12,
  full: 999,
} as const;

export const FontSize = {
  xs: 12,
  sm: 14,
  md: 16,
  lg: 18,
  xl: 22,
  xxl: 28,
} as const;

export const Fonts = Platform.select({
  ios: { mono: 'ui-monospace' },
  default: { mono: 'monospace' },
  web: { mono: 'var(--font-mono)' },
});
