package com.moneylog.backend.common.domain;

/** 수입/지출 구분. 값이 2개로 고정이라 별도 테이블 대신 ENUM으로 둔다. */
public enum TransactionType {
    INCOME, EXPENSE
}
