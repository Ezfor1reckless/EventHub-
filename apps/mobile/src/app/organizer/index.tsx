import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native';

import {
  Button,
  Card,
  Divider,
  EmptyState,
  ErrorAlert,
  EventStatusBadge,
  LoadingBlock,
} from '@/components/ui';
import { api } from '@/lib/api/client';
import { useAuth, useIsOrganizer } from '@/lib/auth/auth-context';
import type { EventResponse } from '@/lib/api/types';
import { Colors, FontSize, Spacing } from '@/constants/theme';

/**
 * 我组织的活动（只读 + 发布）。
 *
 * 创建与编辑活动刻意不放在移动端——策划里移动端的范围是浏览、报名、票券、
 * 扫码签到和通知，活动管理属于 Web 端。与其做一个功能残缺的管理页
 * （比如表单缺字段、改不了容量），不如让这里保持只读，用户知道该去哪儿操作。
 */
export default function OrganizerEventsScreen() {
  const { status: authStatus } = useAuth();
  const isOrganizer = useIsOrganizer();
  const router = useRouter();
  const queryClient = useQueryClient();

  const eventsQuery = useQuery({
    queryKey: ['events', 'mine'],
    queryFn: () => api.me.events({ size: 50 }),
    enabled: authStatus === 'authenticated',
  });

  const publishMutation = useMutation({
    mutationFn: (id: number) => api.events.publish(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['events'] }),
  });

  if (authStatus === 'loading') return <LoadingBlock label="正在确认登录状态…" />;

  if (authStatus === 'anonymous') {
    return (
      <View style={styles.padded}>
        <EmptyState
          title="需要先登录"
          description="登录后可以查看自己组织的活动。"
          action={<Button title="去登录" onPress={() => router.push('/login')} />}
        />
      </View>
    );
  }

  if (eventsQuery.isLoading) return <LoadingBlock />;
  if (eventsQuery.error) {
    return (
      <View style={styles.padded}>
        <ErrorAlert error={eventsQuery.error} />
      </View>
    );
  }

  const items = eventsQuery.data?.items ?? [];

  return (
    <FlatList
      data={items}
      keyExtractor={(item) => String(item.id)}
      contentContainerStyle={styles.listContent}
      refreshControl={
        <RefreshControl
          refreshing={eventsQuery.isRefetching}
          onRefresh={() => void eventsQuery.refetch()}
        />
      }
      ListHeaderComponent={
        publishMutation.error ? <ErrorAlert error={publishMutation.error} /> : null
      }
      ListEmptyComponent={
        isOrganizer ? (
          <EmptyState
            title="你还没有创建过活动"
            description="活动需要在 Web 端创建，创建后这里就能看到。"
          />
        ) : (
          <EmptyState
            title="还没有组织过活动"
            description="在 Web 端创建任意一个活动即可获得组织者角色。"
          />
        )
      }
      ListFooterComponent={
        items.length > 0 ? (
          <Text style={styles.footerNote}>
            创建、编辑活动与现场签到码请使用 Web 端。
          </Text>
        ) : null
      }
      renderItem={({ item }) => (
        <OrganizerEventRow
          event={item}
          onPublish={() => publishMutation.mutate(item.id)}
          publishing={publishMutation.isPending}
        />
      )}
    />
  );
}

function OrganizerEventRow({
  event,
  onPublish,
  publishing,
}: {
  event: EventResponse;
  onPublish: () => void;
  publishing: boolean;
}) {
  return (
    <Card>
      <View style={styles.rowHeader}>
        <Text style={styles.title} numberOfLines={2}>
          {event.title}
        </Text>
        <EventStatusBadge status={event.status} />
      </View>
      <Text style={styles.meta}>
        {formatDateTime(event.startAt)} · {event.location}
      </Text>
      <Text style={styles.meta}>
        已确认 {event.confirmedCount} / {event.capacity}
        {event.waitlistedCount > 0 ? ` · 候补 ${event.waitlistedCount}` : ''}
      </Text>

      {event.status === 'DRAFT' ? (
        <>
          <Divider />
          <Button title="发布" loading={publishing} onPress={onPublish} />
        </>
      ) : null}
    </Card>
  );
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const styles = StyleSheet.create({
  padded: { flex: 1, padding: Spacing.lg, backgroundColor: Colors.background },
  listContent: { padding: Spacing.lg, gap: Spacing.md, flexGrow: 1 },
  rowHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.sm },
  title: { flex: 1, fontSize: FontSize.md, fontWeight: '600', color: Colors.text },
  meta: { fontSize: FontSize.sm, color: Colors.textMuted },
  footerNote: {
    textAlign: 'center',
    fontSize: FontSize.xs,
    color: Colors.textFaint,
    paddingVertical: Spacing.lg,
  },
});
