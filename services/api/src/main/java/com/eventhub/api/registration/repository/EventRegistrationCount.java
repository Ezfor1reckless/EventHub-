package com.eventhub.api.registration.repository;

/**
 * 按活动分组的报名计数投影，供活动列表一次性取回所有计数，避免 N+1。
 */
public record EventRegistrationCount(Long eventId, Long total) {
}
