import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type RefreshControlProps,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';

import { ApiError } from '@/lib/api/client';
import type { EventStatus, RegistrationStatus } from '@/lib/api/types';
import { Colors, FontSize, Radius, Spacing } from '@/constants/theme';

// ---------------------------------------------------------------------------
// 布局
// ---------------------------------------------------------------------------

export function Screen({
  children,
  scroll = true,
  refreshControl,
  contentStyle,
}: {
  children: ReactNode;
  scroll?: boolean;
  /** 类型必须收窄到 RefreshControlProps：ScrollView 只接受这一种元素，
   *  写成通用的 ReactElement 会被 TS 拒绝。 */
  refreshControl?: React.ReactElement<RefreshControlProps>;
  contentStyle?: ViewStyle;
}) {
  if (!scroll) {
    return <View style={[styles.screen, contentStyle]}>{children}</View>;
  }
  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={[styles.screenContent, contentStyle]}
      keyboardShouldPersistTaps="handled"
      refreshControl={refreshControl}>
      {children}
    </ScrollView>
  );
}

export function Card({
  children,
  style,
}: {
  children: ReactNode;
  style?: ViewStyle;
}) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function Divider() {
  return <View style={styles.divider} />;
}

// ---------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled = false,
  loading = false,
  style,
}: {
  title: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
}) {
  const inactive = disabled || loading;
  return (
    <Pressable
      onPress={inactive ? undefined : onPress}
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive }}
      style={({ pressed }) => [
        styles.button,
        buttonVariants[variant],
        pressed && !inactive && styles.buttonPressed,
        inactive && styles.buttonDisabled,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator
          size="small"
          color={variant === 'primary' || variant === 'danger' ? '#FFFFFF' : Colors.text}
        />
      ) : (
        <Text style={[styles.buttonText, buttonTextVariants[variant]]}>{title}</Text>
      )}
    </Pressable>
  );
}

const buttonVariants: Record<ButtonVariant, ViewStyle> = {
  primary: { backgroundColor: Colors.primary },
  secondary: { backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.borderStrong },
  danger: { backgroundColor: Colors.danger },
  ghost: { backgroundColor: 'transparent' },
};

const buttonTextVariants: Record<ButtonVariant, { color: string }> = {
  primary: { color: Colors.onPrimary },
  secondary: { color: Colors.text },
  danger: { color: '#FFFFFF' },
  ghost: { color: Colors.textMuted },
};

// ---------------------------------------------------------------------------
// 表单
// ---------------------------------------------------------------------------

export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {children}
      {error ? (
        <Text style={styles.fieldError}>{error}</Text>
      ) : hint ? (
        <Text style={styles.fieldHint}>{hint}</Text>
      ) : null}
    </View>
  );
}

export function Input({ style, ...props }: TextInputProps) {
  return (
    <TextInput
      placeholderTextColor={Colors.textFaint}
      style={[styles.input, style]}
      {...props}
    />
  );
}

// ---------------------------------------------------------------------------
// 徽章
// ---------------------------------------------------------------------------

export function Badge({
  label,
  tone = 'neutral',
}: {
  label: string;
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
}) {
  const tones = {
    neutral: { bg: '#F1F5F9', fg: Colors.textMuted },
    success: { bg: Colors.successBg, fg: Colors.success },
    warning: { bg: Colors.warningBg, fg: Colors.warning },
    danger: { bg: Colors.dangerBg, fg: Colors.danger },
    info: { bg: Colors.infoBg, fg: Colors.info },
  } as const;
  const { bg, fg } = tones[tone];
  return (
    <View style={[styles.badge, { backgroundColor: bg }]}>
      <Text style={[styles.badgeText, { color: fg }]}>{label}</Text>
    </View>
  );
}

const EVENT_STATUS: Record<
  EventStatus,
  { label: string; tone: 'neutral' | 'success' | 'warning' | 'danger' }
> = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  PUBLISHED: { label: '报名中', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'danger' },
  FINISHED: { label: '已结束', tone: 'neutral' },
};

const REGISTRATION_STATUS: Record<
  RegistrationStatus,
  { label: string; tone: 'neutral' | 'success' | 'warning' | 'danger' | 'info' }
> = {
  PENDING: { label: '待处理', tone: 'warning' },
  CONFIRMED: { label: '已确认', tone: 'success' },
  WAITLISTED: { label: '候补中', tone: 'warning' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  CHECKED_IN: { label: '已签到', tone: 'info' },
};

export function EventStatusBadge({ status }: { status: EventStatus }) {
  const config = EVENT_STATUS[status];
  return <Badge label={config.label} tone={config.tone} />;
}

export function RegistrationStatusBadge({ status }: { status: RegistrationStatus }) {
  const config = REGISTRATION_STATUS[status];
  return <Badge label={config.label} tone={config.tone} />;
}

// ---------------------------------------------------------------------------
// 反馈
// ---------------------------------------------------------------------------

export function LoadingBlock({ label = '加载中…' }: { label?: string }) {
  return (
    <View style={styles.centerBlock}>
      <ActivityIndicator color={Colors.primary} />
      <Text style={styles.mutedText}>{label}</Text>
    </View>
  );
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;

  let message = '发生未知错误';
  let code: string | null = null;

  if (error instanceof ApiError) {
    message = error.message;
    code = error.code;
  } else if (error instanceof Error) {
    message = error.message;
  }

  return (
    <View style={styles.errorBox}>
      <Text style={styles.errorText}>{message}</Text>
      {code ? <Text style={styles.errorCode}>错误码：{code}</Text> : null}
    </View>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <View style={styles.emptyBox}>
      <Text style={styles.emptyTitle}>{title}</Text>
      {description ? <Text style={styles.emptyDescription}>{description}</Text> : null}
      {action ? <View style={styles.emptyAction}>{action}</View> : null}
    </View>
  );
}

// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Colors.background },
  screenContent: { padding: Spacing.lg, gap: Spacing.md },

  card: {
    backgroundColor: Colors.surface,
    borderRadius: Radius.lg,
    borderWidth: 1,
    borderColor: Colors.border,
    padding: Spacing.lg,
    gap: Spacing.sm,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: Colors.border,
    marginVertical: Spacing.sm,
  },

  button: {
    minHeight: 44, // iOS HIG 的最小可点区域
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.lg,
  },
  buttonPressed: { opacity: 0.8 },
  buttonDisabled: { opacity: 0.5 },
  buttonText: { fontSize: FontSize.sm, fontWeight: '600' },

  field: { gap: Spacing.xs },
  fieldLabel: { fontSize: FontSize.sm, fontWeight: '500', color: Colors.text },
  fieldHint: { fontSize: FontSize.xs, color: Colors.textMuted },
  fieldError: { fontSize: FontSize.xs, color: Colors.danger },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: Colors.borderStrong,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.md,
    fontSize: FontSize.sm,
    color: Colors.text,
    backgroundColor: Colors.surface,
  },

  badge: {
    alignSelf: 'flex-start',
    borderRadius: Radius.full,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 2,
  },
  badgeText: { fontSize: FontSize.xs, fontWeight: '600' },

  centerBlock: { alignItems: 'center', justifyContent: 'center', padding: Spacing.xxl, gap: Spacing.sm },
  mutedText: { fontSize: FontSize.sm, color: Colors.textMuted },

  errorBox: {
    backgroundColor: Colors.dangerBg,
    borderWidth: 1,
    borderColor: '#FECACA',
    borderRadius: Radius.md,
    padding: Spacing.md,
    gap: Spacing.xs,
  },
  errorText: { fontSize: FontSize.sm, color: Colors.danger },
  errorCode: { fontSize: FontSize.xs, color: Colors.danger, opacity: 0.8 },

  emptyBox: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: Colors.borderStrong,
    borderRadius: Radius.lg,
    padding: Spacing.xl,
    alignItems: 'center',
    gap: Spacing.xs,
  },
  emptyTitle: { fontSize: FontSize.sm, fontWeight: '600', color: Colors.text },
  emptyDescription: { fontSize: FontSize.sm, color: Colors.textMuted, textAlign: 'center' },
  emptyAction: { marginTop: Spacing.md, alignSelf: 'stretch' },
});
