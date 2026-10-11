"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ErrorAlert } from "@/components/ui/feedback";

/**
 * 校验规则与后端 RegisterRequest 保持一致。
 * 前端校验是为了即时反馈，不是安全边界——后端仍然会独立校验一遍。
 */
const schema = z.object({
  email: z.string().min(1, "请输入邮箱").email("邮箱格式不正确"),
  nickname: z.string().min(1, "请输入昵称").max(64, "昵称不能超过 64 个字符"),
  password: z
    .string()
    .min(8, "密码至少 8 位")
    .max(72, "密码不能超过 72 位")
    .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, "密码需同时包含字母和数字"),
  phone: z
    .string()
    .optional()
    .refine((value) => !value || /^1[3-9]\d{9}$/.test(value), "手机号格式不正确"),
});

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [submitError, setSubmitError] = useState<unknown>(null);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", nickname: "", password: "", phone: "" },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await register({
        email: values.email,
        password: values.password,
        nickname: values.nickname,
        phone: values.phone || undefined,
      });
      router.push("/");
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

  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-16">
      <h1 className="mb-1 text-2xl font-semibold text-slate-900">注册</h1>
      <p className="mb-6 text-sm text-slate-500">
        已有账号？
        <Link href="/login" className="ml-1 text-slate-900 underline">
          去登录
        </Link>
      </p>

      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {submitError ? <ErrorAlert error={submitError} /> : null}

        <Field label="邮箱" htmlFor="email" error={form.formState.errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            invalid={Boolean(form.formState.errors.email)}
            {...form.register("email")}
          />
        </Field>

        <Field
          label="昵称"
          htmlFor="nickname"
          error={form.formState.errors.nickname?.message}
        >
          <Input
            id="nickname"
            autoComplete="nickname"
            invalid={Boolean(form.formState.errors.nickname)}
            {...form.register("nickname")}
          />
        </Field>

        <Field
          label="密码"
          htmlFor="password"
          hint="至少 8 位，需同时包含字母和数字"
          error={form.formState.errors.password?.message}
        >
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(form.formState.errors.password)}
            {...form.register("password")}
          />
        </Field>

        <Field
          label="手机号（选填）"
          htmlFor="phone"
          error={form.formState.errors.phone?.message}
        >
          <Input
            id="phone"
            type="tel"
            autoComplete="tel"
            placeholder="13800138000"
            invalid={Boolean(form.formState.errors.phone)}
            {...form.register("phone")}
          />
        </Field>

        <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "注册中…" : "注册"}
        </Button>
      </form>
    </div>
  );
}
