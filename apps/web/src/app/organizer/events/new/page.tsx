"use client";

import { Suspense } from "react";
import { api } from "@/lib/api/client";
import { RequireAuth } from "@/components/require-auth";
import { EventForm } from "@/components/event-form";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { LoadingBlock } from "@/components/ui/feedback";

export default function NewEventPage() {
  return (
    <Suspense fallback={<LoadingBlock />}>
      <RequireAuth>
        <NewEvent />
      </RequireAuth>
    </Suspense>
  );
}

function NewEvent() {
  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="mb-1 text-2xl font-semibold text-slate-900">创建活动</h1>
      <p className="mb-6 text-sm text-slate-500">
        创建后活动是<b>草稿</b>状态，只有你能看到。确认无误后再发布，
        其他用户才能浏览和报名。
      </p>

      <Card>
        <CardHeader title="活动信息" />
        <CardBody>
          <EventForm
            submitLabel="创建为草稿"
            onSubmit={(payload) => api.events.create(payload)}
          />
        </CardBody>
      </Card>
    </div>
  );
}
