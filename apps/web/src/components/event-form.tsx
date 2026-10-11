"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/field";
import { ErrorAlert } from "@/components/ui/feedback";
import type { EventRequest, EventResponse } from "@/lib/api/types";

/** ISO 时间戳 → <input type="datetime-local"> 需要的本地时间字符串。 */
function toLocalInputValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

const schema = z
  .object({
    title: z.string().min(1, "请输入活动标题").max(200, "标题不能超过 200 字"),
    description: z.string().max(5000, "详情不能超过 5000 字").optional(),
    location: z.string().min(1, "请输入活动地点").max(255, "地点不能超过 255 字"),
    startAt: z.string().min(1, "请选择开始时间"),
    endAt: z.string().min(1, "请选择结束时间"),
    // 用 z.number() 而不是 z.coerce.number()：后者的输入类型是 unknown，
    // 与表单值类型对不上，会让 zodResolver 的泛型推导失败。
    // 字符串到数字的转换交给 react-hook-form 的 valueAsNumber 完成。
    capacity: z
      .number({ message: "容量必须是数字" })
      .int("容量必须是整数")
      .min(1, "容量至少为 1")
      .max(100000, "容量不能超过 100000"),
  })
  // 与后端 BusinessException(EVENT_TIME_INVALID) 同一条规则。
  // 前端先拦一道是为了即时反馈，后端仍然会独立校验。
  .refine((values) => new Date(values.endAt) > new Date(values.startAt), {
    message: "结束时间必须晚于开始时间",
    path: ["endAt"],
  });

type FormValues = z.infer<typeof schema>;

export function EventForm({
  initial,
  submitLabel,
  onSubmit,
}: {
  initial?: EventResponse;
  submitLabel: string;
  onSubmit: (payload: EventRequest) => Promise<unknown>;
}) {
  const router = useRouter();
  const [submitError, setSubmitError] = useState<unknown>(null);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: initial?.title ?? "",
      description: initial?.description ?? "",
      location: initial?.location ?? "",
      startAt: initial ? toLocalInputValue(initial.startAt) : "",
      endAt: initial ? toLocalInputValue(initial.endAt) : "",
      capacity: initial?.capacity ?? 20,
    },
  });

  const handleSubmit = form.handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await onSubmit({
        title: values.title,
        description: values.description || null,
        location: values.location,
        // 本地时间 → UTC ISO。浏览器知道用户的时区，转换必须在这里做；
        // 后端一律按 UTC 存储，不做时区猜测。
        startAt: new Date(values.startAt).toISOString(),
        endAt: new Date(values.endAt).toISOString(),
        capacity: values.capacity,
      });
      router.push("/organizer/events");
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in values) {
            form.setError(field as keyof FormValues, { message });
          }
        }
      }
      setSubmitError(error);
    }
  });

  const errors = form.formState.errors;

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {submitError ? <ErrorAlert error={submitError} /> : null}

      <Field label="活动标题" htmlFor="title" error={errors.title?.message}>
        <Input
          id="title"
          placeholder="周末羽毛球"
          invalid={Boolean(errors.title)}
          {...form.register("title")}
        />
      </Field>

      <Field label="活动地点" htmlFor="location" error={errors.location?.message}>
        <Input
          id="location"
          placeholder="朝阳区全民健身中心 3 号馆"
          invalid={Boolean(errors.location)}
          {...form.register("location")}
        />
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="开始时间" htmlFor="startAt" error={errors.startAt?.message}>
          <Input
            id="startAt"
            type="datetime-local"
            invalid={Boolean(errors.startAt)}
            {...form.register("startAt")}
          />
        </Field>
        <Field label="结束时间" htmlFor="endAt" error={errors.endAt?.message}>
          <Input
            id="endAt"
            type="datetime-local"
            invalid={Boolean(errors.endAt)}
            {...form.register("endAt")}
          />
        </Field>
      </div>

      <Field
        label="报名人数上限"
        htmlFor="capacity"
        hint="满员后新的报名会自动进入候补队列"
        error={errors.capacity?.message}
      >
        <Input
          id="capacity"
          type="number"
          min={1}
          invalid={Boolean(errors.capacity)}
          {...form.register("capacity", { valueAsNumber: true })}
        />
      </Field>

      <Field
        label="活动详情"
        htmlFor="description"
        error={errors.description?.message}
      >
        <Textarea
          id="description"
          rows={6}
          placeholder="活动流程、注意事项、需要自带什么…"
          invalid={Boolean(errors.description)}
          {...form.register("description")}
        />
      </Field>

      <div className="flex gap-3 pt-2">
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "提交中…" : submitLabel}
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
        >
          取消
        </Button>
      </div>
    </form>
  );
}
