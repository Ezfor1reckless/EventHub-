import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocalSearchParams, useRouter } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import {
  Button,
  Card,
  Divider,
  ErrorAlert,
  EventStatusBadge,
  LoadingBlock,
  RegistrationStatusBadge,
  Screen,
} from '@/components/ui';
import { api } from '@/lib/api/client';
import { analytics } from '@/lib/analytics/matomo';
import { useAuth } from '@/lib/auth/auth-context';
import { Colors, FontSize, Spacing } from '@/constants/theme';

export default function EventDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const eventId = Number(id);
  const { status: authStatus } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const eventQuery = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => api.events.get(eventId),
    enabled: Number.isInteger(eventId) && eventId > 0,
  });

  const registrationQuery = useQuery({
    queryKey: ['registration', 'mine', eventId],
    queryFn: () => api.registrations.findMine(eventId),
    enabled: authStatus === 'authenticated' && Number.isInteger(eventId),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['event', eventId] });
    void queryClient.invalidateQueries({ queryKey: ['registration', 'mine', eventId] });
    void queryClient.invalidateQueries({ queryKey: ['registrations', 'mine'] });
    void queryClient.invalidateQueries({ queryKey: ['events'] });
  };

  const registerMutation = useMutation({
    mutationFn: () => api.registrations.create(eventId),
    onSuccess: (registration) => {
      analytics.registerEvent(eventId, registration.status);
      refresh();
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (registrationId: number) => api.registrations.cancel(registrationId),
    onSuccess: () => {
      analytics.cancelRegistration(eventId);
      refresh();
    },
  });

  if (eventQuery.isLoading) {
    return (
      <Screen scroll={false}>
        <LoadingBlock />
      </Screen>
    );
  }

  if (eventQuery.error || !eventQuery.data) {
    return (
      <Screen>
        <ErrorAlert error={eventQuery.error ?? new Error('活动不存在或不可见')} />
        <Link href="/" asChild>
          <Button title="返回活动列表" variant="secondary" onPress={() => router.back()} />
        </Link>
      </Screen>
    );
  }

  const event = eventQuery.data;
  const registration = registrationQuery.data;
  const actionError = registerMutation.error ?? cancelMutation.error;
  const pending = registerMutation.isPending || cancelMutation.isPending;
  const full = event.seatsRemaining <= 0;

  return (
    <Screen>
      <View style={styles.header}>
        <Text style={styles.title}>{event.title}</Text>
        <EventStatusBadge status={event.status} />
      </View>

      <Card>
        <DetailRow label="时间" value={formatRange(event.startAt, event.endAt)} />
        <Divider />
        <DetailRow label="地点" value={event.location} />
        <Divider />
        <DetailRow
          label="名额"
          value={`${event.confirmedCount} / ${event.capacity} 已确认${
            event.waitlistedCount > 0 ? `，${event.waitlistedCount} 人候补` : ''
          }`}
        />
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>报名</Text>
        <Text style={[styles.seats, full && styles.seatsFull]}>
          {full ? '名额已满' : `剩余 ${event.seatsRemaining} 个名额`}
        </Text>

        {actionError ? <ErrorAlert error={actionError} /> : null}

        {authStatus === 'loading' ? (
          <LoadingBlock label="正在确认登录状态…" />
        ) : authStatus === 'anonymous' ? (
          <>
            <Button
              title="登录后报名"
              onPress={() =>
                router.push({
                  pathname: '/login',
                  // 登录后回到这个活动，而不是丢回列表
                  params: { next: `/events/${eventId}` },
                })
              }
            />
            <Text style={styles.hint}>
              还没有账号？
              <Link href="/register" style={styles.link}>
                {' '}
                立即注册
              </Link>
            </Text>
          </>
        ) : registrationQuery.isLoading ? (
          <LoadingBlock label="正在查询报名状态…" />
        ) : (
          <>
            {registration ? (
              <View style={styles.statusRow}>
                <RegistrationStatusBadge status={registration.status} />
                {registration.status === 'WAITLISTED' ? (
                  <Text style={styles.hint}>名额空出后会按候补顺序自动递补</Text>
                ) : null}
              </View>
            ) : null}

            {registration &&
            (registration.status === 'CONFIRMED' || registration.status === 'CHECKED_IN') ? (
              <>
                <Text style={styles.hint}>票券码</Text>
                <Text style={styles.ticket}>{registration.ticketCode}</Text>
              </>
            ) : null}

            {registration && registration.status !== 'CANCELLED' ? (
              <Button
                title={
                  registration.status === 'CHECKED_IN'
                    ? '已签到，无法取消'
                    : registration.status === 'WAITLISTED'
                      ? '取消候补'
                      : '取消报名'
                }
                variant="secondary"
                loading={pending}
                disabled={registration.status === 'CHECKED_IN'}
                onPress={() => cancelMutation.mutate(registration.id)}
              />
            ) : (
              <Button
                title={registration ? '重新报名' : '立即报名'}
                loading={pending}
                disabled={event.status !== 'PUBLISHED'}
                onPress={() => registerMutation.mutate()}
              />
            )}

            <Text style={styles.hint}>名额满时会自动进入候补队列，无需重复提交。</Text>
          </>
        )}
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>活动详情</Text>
        <Text style={styles.description}>
          {event.description ?? '组织者未填写活动详情。'}
        </Text>
      </Card>
    </Screen>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

function formatRange(startIso: string, endIso: string): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  const format = (iso: string) => {
    const d = new Date(iso);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };
  return `${format(startIso)} — ${format(endIso)}`;
}

const styles = StyleSheet.create({
  header: { gap: Spacing.sm, marginBottom: Spacing.sm },
  title: { fontSize: FontSize.xl, fontWeight: '700', color: Colors.text },
  sectionTitle: { fontSize: FontSize.sm, fontWeight: '600', color: Colors.text },
  seats: { fontSize: FontSize.lg, fontWeight: '600', color: Colors.success },
  seatsFull: { color: Colors.warning },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap' },
  ticket: {
    fontFamily: 'monospace',
    fontSize: FontSize.md,
    letterSpacing: 2,
    color: Colors.text,
  },
  hint: { fontSize: FontSize.xs, color: Colors.textMuted },
  link: { color: Colors.text, textDecorationLine: 'underline' },
  description: { fontSize: FontSize.sm, color: Colors.textMuted, lineHeight: 20 },
  row: { flexDirection: 'row', gap: Spacing.md },
  rowLabel: { width: 48, fontSize: FontSize.sm, color: Colors.textFaint },
  rowValue: { flex: 1, fontSize: FontSize.sm, color: Colors.text },
});
