package com.moneylog.backend.common.exception;

import org.springframework.http.HttpStatus;

/** 도메인별 에러 코드 enum이 구현한다. name()이 곧 응답의 code 값이 된다. */
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getMessage();
    String name();
}
