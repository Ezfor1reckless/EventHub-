"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuth } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ErrorAlert, LoadingBlock } from "@/components/ui/feedback";

const schema = z.object({
  email: z.string().min(1, "请输入邮箱").email("邮箱格式不正确"),
  password: z.string().min(1, "请输入密码"),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-16">
      <h1 className="mb-1 text-2xl font-semibold text-slate-900">登录</h1>
      <p className="mb-6 text-sm text-slate-500">
        还没有账号？
        <Link href="/register" className="ml-1 text-slate-900 underline">
          立即注册
        </Link>
      </p>
      <Suspense fallback={<LoadingBlock />}>
        <LoginForm />
      </Suspense>
    </div>
  );
}

function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [submitError, setSubmitError] = useState<unknown>(null);

  // 从受保护页面跳过来的话，登录后回到原来那一页
  const next = searchParams.get("next") ?? "/";

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await login(values.email, values.password);
      router.push(next.startsWith("/") ? next : "/");
    } catch (error) {
      // 后端把字段级校验错误放在 data 里（例如邮箱格式），映射回表单
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field === "email" || field === "password") {
            form.setError(field, { message });
          }
        }
      }
      setSubmitError(error);
    }
  });

  return (
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

      <Field label="密码" htmlFor="password" error={form.formState.errors.password?.message}>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          invalid={Boolean(form.formState.errors.password)}
          {...form.register("password")}
        />
      </Field>

      <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
        {form.formState.isSubmitting ? "登录中…" : "登录"}
      </Button>
    </form>
  );
}
