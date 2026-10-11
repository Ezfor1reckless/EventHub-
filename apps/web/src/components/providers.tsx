"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-context";
import { ApiError } from "@/lib/api/client";

export function Providers({ children }: { children: ReactNode }) {
  // 在 state 里创建 QueryClient：模块级单例在 SSR 下会跨请求共享缓存，
  // 导致一个用户的数据泄露给另一个用户。
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: (failureCount, error) => {
              // 4xx 是确定性失败，重试只是浪费时间：
              // 参数错了重试一百次还是错，403 重试也不会变成 200。
              if (error instanceof ApiError && error.status < 500) {
                return false;
              }
              return failureCount < 2;
            },
          },
          mutations: {
            retry: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
