import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useRouter } from 'expo-router';
import { useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';

import {
  Button,
  Card,
  Divider,
  EmptyState,
  ErrorAlert,
  LoadingBlock,
  RegistrationStatusBadge,
} from '@/components/ui';
import { api } from '@/lib/api/client';
import { useAuth } from '@/lib/auth/auth-context';
import type { RegistrationResponse } from '@/lib/api/types';
import { Colors, FontSize, Spacing } from '@/constants/theme';

export default function TicketsScreen() {
  const { status: authStatus } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState<number | null>(null);

  const registrationsQuery = useQuery({
    queryKey: ['registrations', 'mine'],
    queryFn: () => api.me.registrations({ size: 50 }),
    enabled: authStatus === 'authenticated',
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => api.registrations.cancel(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['registrations', 'mine'] });
      void queryClient.invalidateQueries({ queryKey: ['events'] });
    },
  });

  if (authStatus === 'loading') {
    return <LoadingBlock label="正在确认登录状态…" />;
  }

  if (authStatus === 'anonymous') {
    return (
      <View style={styles.padded}>
        <EmptyState
          title="登录后查看票券"
          description="报名成功的活动会在这里生成电子票券。"
          action={
            <Button title="去登录" onPress={() => router.push('/login')} />
          }
        />
      </View>
    );
  }

  if (registrationsQuery.isLoading) return <LoadingBlock />;
  if (registrationsQuery.error) {
    return (
      <View style={styles.padded}>
        <ErrorAlert error={registrationsQuery.error} />
      </View>
    );
  }

  const items = registrationsQuery.data?.items ?? [];

  return (
    <FlatList
      data={items}
      keyExtractor={(item) => String(item.id)}
      contentContainerStyle={styles.listContent}
      refreshControl={
        <RefreshControl
          refreshing={registrationsQuery.isRefetching}
          onRefresh={() => void registrationsQuery.refetch()}
        />
      }
      ListHeaderComponent={
        cancelMutation.error ? <ErrorAlert error={cancelMutation.error} /> : null
      }
      ListEmptyComponent={
        <EmptyState
          title="还没有报名任何活动"
          description="去「活动」页看看有什么正在报名。"
          action={
            <Button
              title="浏览活动"
              variant="secondary"
              onPress={() => router.push('/')}
            />
          }
        />
      }
      renderItem={({ item }) => (
        <TicketRow
          registration={item}
          expanded={expanded === item.id}
          onToggle={() => setExpanded(expanded === item.id ? null : item.id)}
          onCancel={() => cancelMutation.mutate(item.id)}
          cancelling={cancelMutation.isPending}
        />
      )}
    />
  );
}

function TicketRow({
  registration,
  expanded,
  onToggle,
  onCancel,
  cancelling,
}: {
  registration: RegistrationResponse;
  expanded: boolean;
  onToggle: () => void;
  onCancel: () => void;
  cancelling: boolean;
}) {
  const { event, status } = registration;
  const canCancel = status !== 'CANCELLED' && status !== 'CHECKED_IN';
  const hasTicket = status === 'CONFIRMED' || status === 'CHECKED_IN';

  return (
    <Card>
      <Link href={`/events/${event.id}`} asChild>
        <Pressable>
          <Text style={styles.title}>{event.title}</Text>
          <Text style={styles.meta}>
            {formatDate(event.startAt)} · {event.location}
          </Text>
        </Pressable>
      </Link>

      <Divider />

      <View style={styles.statusRow}>
        <RegistrationStatusBadge status={status} />
      </View>

      {status === 'WAITLISTED' ? (
        <Text style={styles.waitlistNote}>
          你在候补队列中。有人取消时会按顺序自动递补，并给你发通知。
        </Text>
      ) : null}

      {expanded && hasTicket ? (
        <View style={styles.ticketBox}>
          <QRCode value={registration.ticketCode} size={180} />
          <Text style={styles.ticketLabel}>票券码</Text>
          <Text style={styles.ticketCode}>{registration.ticketCode}</Text>
          <Text style={styles.hint}>
            活动当天向组织者出示这个二维码，或用它完成自助签到。
          </Text>
        </View>
      ) : null}

      <View style={styles.actions}>
        {hasTicket ? (
          <Button
            title={expanded ? '收起票券' : '查看票券'}
            variant="secondary"
            onPress={onToggle}
            style={styles.actionButton}
          />
        ) : null}
        {canCancel ? (
          <Button
            title={status === 'WAITLISTED' ? '取消候补' : '取消报名'}
            variant="ghost"
            loading={cancelling}
            onPress={onCancel}
            style={styles.actionButton}
          />
        ) : null}
      </View>
    </Card>
  );
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const styles = StyleSheet.create({
  padded: { padding: Spacing.lg },
  listContent: { padding: Spacing.lg, gap: Spacing.md, flexGrow: 1 },
  title: { fontSize: FontSize.md, fontWeight: '600', color: Colors.text },
  meta: { fontSize: FontSize.sm, color: Colors.textMuted, marginTop: 2 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm },
  waitlistNote: { fontSize: FontSize.xs, color: Colors.warning, lineHeight: 18 },
  ticketBox: { alignItems: 'center', gap: Spacing.sm, paddingVertical: Spacing.md },
  ticketLabel: { fontSize: FontSize.xs, color: Colors.textFaint },
  ticketCode: {
    fontFamily: 'monospace',
    fontSize: FontSize.md,
    letterSpacing: 2,
    color: Colors.text,
  },
  hint: { fontSize: FontSize.xs, color: Colors.textMuted, textAlign: 'center' },
  actions: { flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.xs },
  actionButton: { flex: 1 },
});
