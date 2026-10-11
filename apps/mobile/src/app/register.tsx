import { useRouter } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Card, ErrorAlert, Field, Input, Screen } from '@/components/ui';
import { ApiError } from '@/lib/api/client';
import { useAuth } from '@/lib/auth/auth-context';
import { validateRegistration } from '@/lib/validation';
import { Colors, FontSize, Spacing } from '@/constants/theme';

export default function RegisterScreen() {
  const { register } = useAuth();
  const router = useRouter();

  const [email, setEmail] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async () => {
    setError(null);

    const errors = validateRegistration({ email, nickname, password, phone });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await register({
        email: email.trim(),
        password,
        nickname: nickname.trim(),
        phone: phone || undefined,
      });
      router.back();
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
        <Text style={styles.title}>注册</Text>
        <Text style={styles.subtitle}>注册后即可报名活动。</Text>

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

        <Field label="昵称" error={fieldErrors.nickname}>
          <Input value={nickname} onChangeText={setNickname} placeholder="怎么称呼你" />
        </Field>

        <Field
          label="密码"
          hint="至少 8 位，需同时包含字母和数字"
          error={fieldErrors.password}>
          <Input
            value={password}
            onChangeText={setPassword}
            placeholder="请设置密码"
            secureTextEntry
            autoCapitalize="none"
            textContentType="newPassword"
          />
        </Field>

        <Field label="手机号（选填）" error={fieldErrors.phone}>
          <Input
            value={phone}
            onChangeText={setPhone}
            placeholder="13800138000"
            keyboardType="phone-pad"
          />
        </Field>

        <Button title="注册" loading={submitting} onPress={() => void handleSubmit()} />

        <View style={styles.footer}>
          <Button
            title="已有账号？去登录"
            variant="ghost"
            onPress={() => router.replace('/login')}
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
