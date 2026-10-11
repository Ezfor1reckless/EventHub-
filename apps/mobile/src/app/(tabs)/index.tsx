import { useInfiniteQuery } from '@tanstack/react-query';
import { Link } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { EventStatusBadge } from '@/components/ui';
import { EmptyState, ErrorAlert } from '@/components/ui';
import { api } from '@/lib/api/client';
import type { EventResponse } from '@/lib/api/types';
import { Colors, FontSize, Radius, Spacing } from '@/constants/theme';

const PAGE_SIZE = 10;

export default function EventsScreen() {
  const {
    data,
    isLoading,
    error,
    refetch,
    isRefetching,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['events', 'list'],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      api.events.list({ page: pageParam, size: PAGE_SIZE, sort: 'startAt' }),
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.page + 1 : undefined),
  });

  const events = data?.pages.flatMap((page) => page.items) ?? [];

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={Colors.primary} />
      </View>
    );
  }

  if (error) {
    return (
      <View style={styles.padded}>
        <ErrorAlert error={error} />
      </View>
    );
  }

  return (
    <FlatList
      data={events}
      keyExtractor={(item) => String(item.id)}
      contentContainerStyle={styles.listContent}
      refreshControl={
        <RefreshControl refreshing={isRefetching} onRefresh={() => void refetch()} />
      }
      onEndReachedThreshold={0.4}
      onEndReached={() => {
        // isFetchingNextPage 判断不能省：没有它，快速滑动会在一次请求未返回前
        // 反复触发 onEndReached，同一页被拉好几次
        if (hasNextPage && !isFetchingNextPage) void fetchNextPage();
      }}
      ListEmptyComponent={
        <EmptyState
          title="还没有已发布的活动"
          description="下拉可以刷新看看有没有新活动。"
        />
      }
      ListFooterComponent={
        isFetchingNextPage ? (
          <View style={styles.footer}>
            <ActivityIndicator color={Colors.textFaint} />
          </View>
        ) : !hasNextPage && events.length > 0 ? (
          <Text style={styles.footerText}>没有更多了</Text>
        ) : null
      }
      renderItem={({ item }) => <EventRow event={item} />}
    />
  );
}

function EventRow({ event }: { event: EventResponse }) {
  const full = event.seatsRemaining <= 0;

  return (
    <Link href={`/events/${event.id}`} asChild>
      <Pressable style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
        <View style={styles.cardHeader}>
          <Text style={styles.cardTitle} numberOfLines={2}>
            {event.title}
          </Text>
          <EventStatusBadge status={event.status} />
        </View>

        <Text style={styles.cardMeta} numberOfLines={1}>
          {formatRange(event.startAt)} · {event.location}
        </Text>

        <View style={styles.cardFooter}>
          <Text style={[styles.seats, full && styles.seatsFull]}>
            {full ? '已满，可候补' : `剩余 ${event.seatsRemaining} / ${event.capacity}`}
          </Text>
          {event.waitlistedCount > 0 ? (
            <Text style={styles.waitlist}>候补 {event.waitlistedCount} 人</Text>
          ) : null}
        </View>
      </Pressable>
    </Link>
  );
}

function formatRange(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  padded: { padding: Spacing.lg },
  listContent: { padding: Spacing.lg, gap: Spacing.md, flexGrow: 1 },

  card: {
    backgroundColor: Colors.surface,
    borderRadius: Radius.lg,
    borderWidth: 1,
    borderColor: Colors.border,
    padding: Spacing.lg,
    gap: Spacing.sm,
  },
  cardPressed: { opacity: 0.7 },
  cardHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.sm },
  cardTitle: { flex: 1, fontSize: FontSize.md, fontWeight: '600', color: Colors.text },
  cardMeta: { fontSize: FontSize.sm, color: Colors.textMuted },
  cardFooter: { flexDirection: 'row', alignItems: 'center', gap: Spacing.md },
  seats: { fontSize: FontSize.sm, color: Colors.success },
  seatsFull: { color: Colors.warning },
  waitlist: { fontSize: FontSize.sm, color: Colors.textFaint },

  footer: { paddingVertical: Spacing.lg },
  footerText: {
    textAlign: 'center',
    paddingVertical: Spacing.lg,
    fontSize: FontSize.xs,
    color: Colors.textFaint,
  },
});
