import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { Button, Card, EmptyState, ErrorAlert, LoadingBlock } from '@/components/ui';
import { api } from '@/lib/api/client';
import { useAuth } from '@/lib/auth/auth-context';
import type { NotificationResponse } from '@/lib/api/types';
import { Colors, FontSize, Spacing } from '@/constants/theme';

export default function NotificationsScreen() {
  const { status: authStatus } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => api.me.notifications({ size: 50 }),
    enabled: authStatus === 'authenticated',
  });

  const markRead = useMutation({
    mutationFn: (id: number) => api.me.markNotificationRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  if (authStatus === 'loading') return <LoadingBlock label="正在确认登录状态…" />;

  if (authStatus === 'anonymous') {
    return (
      <View style={styles.padded}>
        <EmptyState
          title="登录后查看通知"
          description="报名成功、候补转正、活动取消都会在这里提醒你。"
          action={<Button title="去登录" onPress={() => router.push('/login')} />}
        />
      </View>
    );
  }

  if (notificationsQuery.isLoading) return <LoadingBlock />;
  if (notificationsQuery.error) {
    return (
      <View style={styles.padded}>
        <ErrorAlert error={notificationsQuery.error} />
      </View>
    );
  }

  const items = notificationsQuery.data?.items ?? [];

  return (
    <FlatList
      data={items}
      keyExtractor={(item) => String(item.id)}
      contentContainerStyle={styles.listContent}
      refreshControl={
        <RefreshControl
          refreshing={notificationsQuery.isRefetching}
          onRefresh={() => void notificationsQuery.refetch()}
        />
      }
      ListEmptyComponent={
        <EmptyState
          title="还没有收到通知"
          description="报名成功、候补转正、活动取消时都会在这里提醒你。"
        />
      }
      renderItem={({ item }) => (
        <NotificationRow
          notification={item}
          onMarkRead={() => markRead.mutate(item.id)}
          onOpenEvent={() => router.push(`/events/${item.relatedId}`)}
        />
      )}
    />
  );
}

function NotificationRow({
  notification,
  onMarkRead,
  onOpenEvent,
}: {
  notification: NotificationResponse;
  onMarkRead: () => void;
  onOpenEvent: () => void;
}) {
  const unread = notification.readAt === null;
  const hasEvent = notification.relatedType === 'Event' && notification.relatedId;

  return (
    <Card style={unread ? styles.unreadCard : undefined}>
      <View style={styles.row}>
        {/*
          未读同时用圆点和字重两个维度表示。只用颜色区分对色觉障碍用户不友好，
          而且在小屏上单靠一个色点很容易被忽略。
        */}
        <View style={[styles.dot, unread ? styles.dotUnread : styles.dotRead]} />
        <View style={styles.content}>
          <Text style={[styles.title, unread && styles.titleUnread]}>
            {notification.title}
          </Text>
          <Text style={styles.meta}>{formatDateTime(notification.createdAt)}</Text>
          <Text style={styles.body}>{notification.content}</Text>

          <View style={styles.actions}>
            {hasEvent ? (
              <Pressable onPress={onOpenEvent}>
                <Text style={styles.link}>查看活动</Text>
              </Pressable>
            ) : null}
            {unread ? (
              <Pressable onPress={onMarkRead}>
                <Text style={styles.mutedLink}>标记已读</Text>
              </Pressable>
            ) : null}
          </View>
        </View>
      </View>
    </Card>
  );
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const styles = StyleSheet.create({
  padded: { padding: Spacing.lg },
  listContent: { padding: Spacing.lg, gap: Spacing.md, flexGrow: 1 },
  unreadCard: { borderColor: Colors.borderStrong },
  row: { flexDirection: 'row', gap: Spacing.sm },
  dot: { width: 8, height: 8, borderRadius: 4, marginTop: 6 },
  dotUnread: { backgroundColor: Colors.danger },
  dotRead: { backgroundColor: 'transparent' },
  content: { flex: 1, gap: 2 },
  title: { fontSize: FontSize.sm, color: Colors.textMuted },
  titleUnread: { fontWeight: '600', color: Colors.text },
  meta: { fontSize: FontSize.xs, color: Colors.textFaint },
  body: { fontSize: FontSize.sm, color: Colors.textMuted, marginTop: 2, lineHeight: 19 },
  actions: { flexDirection: 'row', gap: Spacing.lg, marginTop: Spacing.sm },
  link: { fontSize: FontSize.xs, color: Colors.text, textDecorationLine: 'underline' },
  mutedLink: { fontSize: FontSize.xs, color: Colors.textMuted },
});
