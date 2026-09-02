package com.moneylog.backend.common.exception;

/** 검증 실패 시 어느 필드가 왜 틀렸는지 프론트에 알려준다. (API 명세 D-11) */
public record FieldErrorDetail(String field, String reason) {
}
