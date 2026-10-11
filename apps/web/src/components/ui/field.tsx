import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/utils";

const CONTROL =
  "block w-full rounded-md border-0 px-3 py-2 text-sm text-slate-900 shadow-sm " +
  "ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 " +
  "focus:ring-2 focus:ring-inset focus:ring-slate-900 disabled:bg-slate-50";

export function Field({
  label,
  htmlFor,
  error,
  hint,
  children,
}: {
  label: string;
  htmlFor?: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {error ? (
        <p className="text-xs text-red-600">{error}</p>
      ) : hint ? (
        <p className="text-xs text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

/** 用 ComponentProps<"input"> 而不是 InputHTMLAttributes —— 前者包含 ref，
 *  这样 react-hook-form 的 register() 展开（其中带 ref）能直接放进来。 */
export function Input({
  className,
  invalid,
  ...props
}: ComponentProps<"input"> & { invalid?: boolean }) {
  return (
    <input
      {...props}
      aria-invalid={invalid || undefined}
      className={cn(CONTROL, invalid && "ring-red-400 focus:ring-red-500", className)}
    />
  );
}

export function Textarea({
  className,
  invalid,
  ...props
}: ComponentProps<"textarea"> & { invalid?: boolean }) {
  return (
    <textarea
      {...props}
      aria-invalid={invalid || undefined}
      className={cn(CONTROL, invalid && "ring-red-400 focus:ring-red-500", className)}
    />
  );
}

export function Select({
  className,
  ...props
}: ComponentProps<"select">) {
  return <select {...props} className={cn(CONTROL, "pr-8", className)} />;
}
