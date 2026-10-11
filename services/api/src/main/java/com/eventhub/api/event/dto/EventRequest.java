package com.eventhub.api.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 创建与更新活动共用同一份表单——更新是全量替换，不是部分更新。
 * 因此接口用 PUT 而非 PATCH：PATCH 语义上允许省略字段，那需要
 * 区分「没传」和「传了 null」，这里没有这个需求，用 PUT 更诚实。
 */
@Schema(description = "活动表单")
public record EventRequest(

        @Schema(example = "周末羽毛球")
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题长度不能超过 200")
        String title,

        @Schema(description = "活动详情，支持长文本")
        @Size(max = 5000, message = "详情长度不能超过 5000")
        String description,

        @Schema(example = "https://cdn.example.com/cover.png")
        @Size(max = 512, message = "封面地址长度不能超过 512")
        String coverUrl,

        @Schema(example = "朝阳区全民健身中心 3 号馆")
        @NotBlank(message = "地点不能为空")
        @Size(max = 255, message = "地点长度不能超过 255")
        String location,

        @Schema(description = "ISO-8601 时间戳", example = "2026-11-01T02:00:00Z")
        @NotNull(message = "开始时间不能为空")
        Instant startAt,

        @Schema(example = "2026-11-01T04:00:00Z")
        @NotNull(message = "结束时间不能为空")
        Instant endAt,

        @Schema(description = "报名人数上限", example = "30")
        @Min(value = 1, message = "容量至少为 1")
        @Max(value = 100000, message = "容量不能超过 100000")
        int capacity
) {
}
