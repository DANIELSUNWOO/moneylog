package com.moneylog.backend.transaction.exception;

import com.moneylog.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransactionErrorCode implements ErrorCode {
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "거래내역을 찾을 수 없습니다."),
    CATEGORY_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "거래 타입과 카테고리 타입이 일치하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
