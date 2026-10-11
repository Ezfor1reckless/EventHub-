import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Suspense } from "react";
import "./globals.css";
import { Providers } from "@/components/providers";
import { SiteHeader } from "@/components/site-header";
import { AnalyticsProvider } from "@/components/analytics-provider";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "EventHub — 社区活动报名与签到",
  description: "浏览社区活动、在线报名、候补递补、扫码签到。",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="zh-CN"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>
          {/*
            导航栏用 usePathname 做高亮、用 useAuth 判断登录态，两者都只有运行时才有值。
            Next 16 的 cacheComponents 要求这类客户端 Hook 待在 <Suspense> 里，
            否则整页无法预渲染。fallback 是个等高占位条，避免布局跳动。
          */}
          <Suspense fallback={<div className="h-14 border-b border-slate-200 bg-white" />}>
            <SiteHeader />
          </Suspense>
          <main className="flex-1">{children}</main>
          <Suspense fallback={null}>
            <AnalyticsProvider />
          </Suspense>
          <footer className="border-t border-slate-200 py-6 text-center text-xs text-slate-400">
            EventHub — 社区活动报名与签到平台
          </footer>
        </Providers>
      </body>
    </html>
  );
}
