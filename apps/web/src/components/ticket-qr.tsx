"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";

/**
 * 票券二维码。
 *
 * 内容就是票券码本身，供组织者用「手工补签」扫码录入。
 * 动态签到码（60 秒有效的那种）是另一个东西，在组织者的活动管理页生成。
 */
export function TicketQr({
  value,
  size = 180,
  alt = "二维码",
}: {
  value: string;
  size?: number;
  alt?: string;
}) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    QRCode.toDataURL(value, { margin: 1, width: size })
      .then((url) => {
        if (!cancelled) setDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [value, size]);

  if (failed) {
    // 生成二维码失败不该让整页崩掉——票券码本身仍然可读、可手工录入
    return (
      <div className="flex size-[180px] items-center justify-center rounded border border-dashed border-slate-300 text-xs text-slate-400">
        二维码生成失败
      </div>
    );
  }

  if (!dataUrl) {
    return (
      <div
        className="animate-pulse rounded bg-slate-100"
        style={{ width: size, height: size }}
      />
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element -- data URL 走 next/image 没有收益，反而多一层无谓的处理
    <img
      src={dataUrl}
      alt={alt}
      width={size}
      height={size}
      className="rounded border border-slate-200 bg-white p-1"
    />
  );
}
