import { CameraView, useCameraPermissions } from 'expo-camera';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button, Card, ErrorAlert, Screen } from '@/components/ui';
import { api } from '@/lib/api/client';
import { analytics } from '@/lib/analytics/matomo';
import { useAuth } from '@/lib/auth/auth-context';
import type { CheckInResponse } from '@/lib/api/types';
import { Colors, FontSize, Radius, Spacing } from '@/constants/theme';

export default function ScanScreen() {
  const { status: authStatus } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [permission, requestPermission] = useCameraPermissions();
  const [result, setResult] = useState<CheckInResponse | null>(null);
  const [error, setError] = useState<unknown>(null);

  // 用 ref 而不是 state 做「已处理」标记：onBarcodeScanned 在相机帧上高频触发，
  // 用 state 会因为闭包拿到旧值而重复提交同一个码。
  const handledRef = useRef(false);

  const scanMutation = useMutation({
    mutationFn: (token: string) => api.checkin.scan(token),
    onSuccess: (checkIn) => {
      setResult(checkIn);
      analytics.checkInSuccess(checkIn.eventId, checkIn.method);
      void queryClient.invalidateQueries({ queryKey: ['registrations', 'mine'] });
      void queryClient.invalidateQueries({ queryKey: ['event', checkIn.eventId] });
    },
    onError: (caught) => {
      setError(caught);
    },
  });

  const handleBarcodeScanned = ({ data }: { data: string }) => {
    if (handledRef.current) return;
    handledRef.current = true;
    setError(null);
    scanMutation.mutate(data);
  };

  const reset = () => {
    handledRef.current = false;
    setResult(null);
    setError(null);
  };

  if (authStatus === 'anonymous') {
    return (
      <Screen>
        <Card>
          <Text style={styles.title}>需要先登录</Text>
          <Text style={styles.hint}>扫码签到需要身份，请先登录。</Text>
          <Button title="去登录" onPress={() => router.push('/login')} />
        </Card>
      </Screen>
    );
  }

  if (!permission) {
    return (
      <Screen>
        <Card>
          <Text style={styles.hint}>正在检查相机权限…</Text>
        </Card>
      </Screen>
    );
  }

  if (!permission.granted) {
    return (
      <Screen>
        <Card>
          <Text style={styles.title}>需要相机权限</Text>
          <Text style={styles.hint}>
            {permission.canAskAgain
              ? '扫码签到要使用相机。'
              : '相机权限已被拒绝，请到系统设置里手动开启后重试。'}
          </Text>
          {permission.canAskAgain ? (
            <Button title="授予相机权限" onPress={() => void requestPermission()} />
          ) : null}
        </Card>
      </Screen>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.cameraWrapper}>
        <CameraView
          style={StyleSheet.absoluteFill}
          facing="back"
          barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
          // 已出结果时传 undefined 停止扫描，避免同一个码被反复提交
          onBarcodeScanned={result || error ? undefined : handleBarcodeScanned}
        />
        <View style={styles.overlay} pointerEvents="none">
          <View style={styles.reticle} />
        </View>
      </View>

      <View style={styles.panel}>
        {scanMutation.isPending ? (
          <Text style={styles.hint}>正在签到…</Text>
        ) : result ? (
          <Card>
            <Text style={styles.successTitle}>签到成功</Text>
            <Text style={styles.hint}>
              票券 {result.ticketCode}
              {'\n'}
              方式：{result.method === 'QR_SCAN' ? '扫码' : '人工补签'}
            </Text>
            <Button title="继续扫描" onPress={reset} />
          </Card>
        ) : error ? (
          <Card>
            <ErrorAlert error={error} />
            <Button title="重新扫描" variant="secondary" onPress={reset} />
          </Card>
        ) : (
          <Card>
            <Text style={styles.title}>对准签到二维码</Text>
            <Text style={styles.hint}>
              组织者投屏的二维码 60 秒失效，过期后请让组织者重新生成。
            </Text>
          </Card>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  cameraWrapper: { flex: 1, overflow: 'hidden', backgroundColor: '#000' },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reticle: {
    width: 220,
    height: 220,
    borderWidth: 2,
    borderColor: '#FFFFFF',
    borderRadius: Radius.lg,
    opacity: 0.8,
  },
  panel: { padding: Spacing.lg, gap: Spacing.md },
  title: { fontSize: FontSize.md, fontWeight: '600', color: Colors.text },
  successTitle: { fontSize: FontSize.lg, fontWeight: '700', color: Colors.success },
  hint: { fontSize: FontSize.sm, color: Colors.textMuted, lineHeight: 19 },
});
