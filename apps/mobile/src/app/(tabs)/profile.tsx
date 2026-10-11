import { useRouter } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { Badge, Button, Card, Divider, EmptyState, LoadingBlock, Screen } from '@/components/ui';
import { useAuth, useIsOrganizer } from '@/lib/auth/auth-context';
import { Colors, FontSize, Spacing } from '@/constants/theme';

export default function ProfileScreen() {
  const { user, status, logout } = useAuth();
  const isOrganizer = useIsOrganizer();
  const router = useRouter();

  if (status === 'loading') return <LoadingBlock label="正在确认登录状态…" />;

  if (status === 'anonymous' || !user) {
    return (
      <View style={styles.padded}>
        <EmptyState
          title="还没有登录"
          description="登录后可以报名活动、查看票券和通知。"
          action={<Button title="去登录" onPress={() => router.push('/login')} />}
        />
        <View style={styles.registerLink}>
          <Button
            title="注册新账号"
            variant="secondary"
            onPress={() => router.push('/register')}
          />
        </View>
      </View>
    );
  }

  return (
    <Screen>
      <Card>
        <Text style={styles.sectionTitle}>账号信息</Text>
        <Divider />
        <InfoRow label="邮箱" value={user.email} />
        <InfoRow label="昵称" value={user.nickname} />
        <InfoRow label="手机号" value={user.phone ?? '未填写'} />
        <View style={styles.rolesRow}>
          <Text style={styles.infoLabel}>角色</Text>
          <View style={styles.roles}>
            {user.roles.map((role) => (
              <Badge
                key={role}
                label={
                  role === 'ADMIN' ? '管理员' : role === 'ORGANIZER' ? '组织者' : '普通用户'
                }
                tone={role === 'ADMIN' ? 'info' : role === 'ORGANIZER' ? 'success' : 'neutral'}
              />
            ))}
          </View>
        </View>
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>快捷入口</Text>
        <Divider />
        <Button
          title="扫码签到"
          variant="secondary"
          onPress={() => router.push('/scan')}
        />
        <Button
          title={isOrganizer ? '我组织的活动' : '我要办活动'}
          variant="secondary"
          onPress={() => router.push('/organizer')}
        />
      </Card>

      <Button
        title="退出登录"
        variant="ghost"
        onPress={() => void logout()}
      />
    </Screen>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  padded: { flex: 1, padding: Spacing.lg, backgroundColor: Colors.background },
  registerLink: { marginTop: Spacing.md },
  sectionTitle: { fontSize: FontSize.sm, fontWeight: '600', color: Colors.text },
  infoRow: { flexDirection: 'row', gap: Spacing.md, paddingVertical: 2 },
  infoLabel: { width: 56, fontSize: FontSize.sm, color: Colors.textFaint },
  infoValue: { flex: 1, fontSize: FontSize.sm, color: Colors.text },
  rolesRow: { flexDirection: 'row', gap: Spacing.md, paddingVertical: 2 },
  roles: { flex: 1, flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
});
