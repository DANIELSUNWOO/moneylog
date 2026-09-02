package com.moneylog.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

/**
 * 모든 응답의 공통 껍데기(response envelope).
 * 성공:  {success:true,  message, data, (meta)}
 * 실패:  {success:false, code, message, data:null}
 * data는 null이어도 항상 내려보낸다(명세가 data:null을 요구). code/meta만 null일 때 생략한다.
 */
@Getter
@JsonPropertyOrder({"success", "code", "message", "data", "meta"})
public class ApiResponse<T> {

    private final boolean success;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String code;

    private final String message;

    private final T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Meta meta;

    private ApiResponse(boolean success, String code, String message, T data, Meta meta) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, null, message, data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data, Meta meta) {
        return new ApiResponse<>(true, null, message, data, meta);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data, null);
    }

    public static ApiResponse<Object> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, null);
    }
}
