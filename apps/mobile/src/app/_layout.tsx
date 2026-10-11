import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { Providers } from '@/components/providers';
import { ScreenTracker } from '@/components/screen-tracker';
import { Colors, FontSize } from '@/constants/theme';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <Providers>
        <StatusBar style="dark" />
        <ScreenTracker />
        <Stack
          screenOptions={{
            headerStyle: { backgroundColor: Colors.surface },
            headerTitleStyle: { fontSize: FontSize.md, color: Colors.text },
            headerTintColor: Colors.text,
            contentStyle: { backgroundColor: Colors.background },
          }}>
          {/* 底部标签页自带导航栏，外层不再重复一个 */}
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="login" options={{ title: '登录' }} />
          <Stack.Screen name="register" options={{ title: '注册' }} />
          <Stack.Screen name="events/[id]" options={{ title: '活动详情' }} />
          <Stack.Screen name="scan" options={{ title: '扫码签到' }} />
          <Stack.Screen name="organizer/index" options={{ title: '我组织的活动' }} />
        </Stack>
      </Providers>
    </SafeAreaProvider>
  );
}
