import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api/client";

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="加载中"
      className={cn(
        "inline-block size-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-900",
        className,
      )}
    />
  );
}

export function LoadingBlock({ label = "加载中…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12 text-sm text-slate-500">
      <Spinner />
      {label}
    </div>
  );
}

/**
 * 错误提示。把 ApiError 的错误码一并显示出来——
 * 用户看到「请求过于频繁」时无法判断该等多久，看到 TOO_MANY_REQUESTS 才能去查。
 */
export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;

  let message = "发生未知错误";
  let code: string | null = null;

  if (error instanceof ApiError) {
    message = error.message;
    code = error.code;
  } else if (error instanceof Error) {
    message = error.message;
  }

  return (
    <div
      role="alert"
      className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
    >
      <p>{message}</p>
      {code ? <p className="mt-1 text-xs text-red-600">错误码：{code}</p> : null}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 px-6 py-12 text-center">
      <p className="text-sm font-medium text-slate-900">{title}</p>
      {description ? (
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      ) : null}
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </div>
  );
}
