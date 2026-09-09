package com.moneylog.backend.category;

import com.moneylog.backend.category.dto.CategoryRequest;
import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.support.ApiTestSupport;
import com.moneylog.backend.transaction.entity.Transaction;
import com.moneylog.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카테고리 수정이 거래 데이터의 정합성을 깨지 않는지 검증한다.
 *
 * 배경(erd.md D-1): transactions.type은 categories.type을 비정규화해 들고 있다.
 * 통계·필터에서 JOIN 없이 집계하려는 의도적 중복이고, 그 대가로 "두 값은 항상 같다"는
 * 불변식을 서비스가 직접 지켜야 한다. TransactionService는 거래 등록·수정 시점에
 * 이 검사를 하지만(getOwnedCategoryWithTypeCheck), 카테고리 쪽 수정 경로도 같은
 * 불변식을 지켜야 완성된다 — 거래가 달린 카테고리의 타입이 바뀌면 기존 거래들은
 * 옛 타입으로 남아 "지출 카테고리에 매달린 수입 거래"가 조용히 생기기 때문이다.
 */
@DisplayName("카테고리 수정과 거래 정합성 (erd.md D-1)")
class CategoryUpdateIntegrityTest extends ApiTestSupport {

    private User me;

    @BeforeEach
    void setUpUser() {
        me = createUser("me@moneylog.test");
    }

    @Test
    @DisplayName("거래가 등록된 카테고리의 타입은 바꿀 수 없다")
    void updateType_whenTransactionsExist_isRejected() throws Exception {
        Category category = createCategory(me, "식비", TransactionType.EXPENSE);
        Transaction transaction = createTransaction(me, category, 10_000L);

        CategoryRequest request = new CategoryRequest("식비", TransactionType.INCOME);

        mockMvc.perform(put("/api/categories/{id}", category.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_TYPE_CHANGE_NOT_ALLOWED"));

        // 거부됐으면 카테고리와 거래의 타입이 여전히 같아야 한다.
        Category unchanged = categoryRepository.findById(category.getId()).orElseThrow();
        Transaction stored = transactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(unchanged.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(stored.getType()).isEqualTo(unchanged.getType());
    }

    @Test
    @DisplayName("거래가 있어도 이름만 바꾸는 것은 허용한다 — 이름은 불변식과 무관하다")
    void updateNameOnly_whenTransactionsExist_isAllowed() throws Exception {
        Category category = createCategory(me, "식비", TransactionType.EXPENSE);
        createTransaction(me, category, 10_000L);

        CategoryRequest request = new CategoryRequest("음식", TransactionType.EXPENSE);

        mockMvc.perform(put("/api/categories/{id}", category.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("음식"));
    }

    @Test
    @DisplayName("거래가 없는 카테고리는 타입을 바꿀 수 있다 — 깨질 데이터가 없다")
    void updateType_whenNoTransactions_isAllowed() throws Exception {
        Category category = createCategory(me, "용돈", TransactionType.EXPENSE);

        CategoryRequest request = new CategoryRequest("용돈", TransactionType.INCOME);

        mockMvc.perform(put("/api/categories/{id}", category.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("INCOME"));
    }
}
