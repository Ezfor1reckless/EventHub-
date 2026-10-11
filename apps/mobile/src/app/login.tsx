import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Card, ErrorAlert, Field, Input, Screen } from '@/components/ui';
import { ApiError } from '@/lib/api/client';
import { useAuth } from '@/lib/auth/auth-context';
import { Colors, FontSize, Spacing } from '@/constants/theme';

export default function LoginScreen() {
  const { login } = useAuth();
  const router = useRouter();
  const { next } = useLocalSearchParams<{ next?: string }>();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async () => {
    setError(null);
    setFieldErrors({});

    // 前端校验只为即时反馈，不是安全边界——后端会独立校验一遍
    if (!email.trim()) {
      setFieldErrors({ email: '请输入邮箱' });
      return;
    }
    if (!password) {
      setFieldErrors({ password: '请输入密码' });
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      // 从受保护页面跳过来的话回到原来那一页
      if (next) router.replace(next as never);
      else router.back();
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors) {
        setFieldErrors(caught.fieldErrors);
      }
      setError(caught);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen>
      <Card>
        <Text style={styles.title}>登录</Text>
        <Text style={styles.subtitle}>还没有账号？可以先注册一个。</Text>

        {error ? <ErrorAlert error={error} /> : null}

        <Field label="邮箱" error={fieldErrors.email}>
          <Input
            value={email}
            onChangeText={setEmail}
            placeholder="you@example.com"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="email-address"
            textContentType="emailAddress"
          />
        </Field>

        <Field label="密码" error={fieldErrors.password}>
          <Input
            value={password}
            onChangeText={setPassword}
            placeholder="请输入密码"
            secureTextEntry
            autoCapitalize="none"
            textContentType="password"
          />
        </Field>

        <Button title="登录" loading={submitting} onPress={() => void handleSubmit()} />

        <View style={styles.footer}>
          <Button
            title="没有账号？去注册"
            variant="ghost"
            onPress={() => router.replace('/register')}
          />
        </View>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: FontSize.xl, fontWeight: '700', color: Colors.text },
  subtitle: { fontSize: FontSize.sm, color: Colors.textMuted },
  footer: { marginTop: Spacing.sm },
});
