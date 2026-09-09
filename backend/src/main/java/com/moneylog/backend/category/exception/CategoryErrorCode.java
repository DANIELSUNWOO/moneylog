package com.moneylog.backend.category.exception;

import com.moneylog.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {
    // 내 것이 아닌 카테고리도 "없음"으로 응답한다. 403이면 존재 여부가 새어나간다.
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "같은 이름과 타입의 카테고리가 이미 있습니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "거래가 등록된 카테고리는 삭제할 수 없습니다."),
    CATEGORY_TYPE_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "거래가 등록된 카테고리의 타입은 변경할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
