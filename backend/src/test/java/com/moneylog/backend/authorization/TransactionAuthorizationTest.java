package com.moneylog.backend.authorization;

import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.support.ApiTestSupport;
import com.moneylog.backend.transaction.dto.TransactionRequest;
import com.moneylog.backend.transaction.entity.Transaction;
import com.moneylog.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-06 인가: "내 데이터는 나만 접근한다".
 *
 * 이 프로젝트가 내세우는 핵심 원칙이므로, 문서가 아니라 실행되는 코드로 증명한다.
 * 남의 리소스에 대한 응답이 403이 아니라 404여야 한다는 것까지 함께 검증한다.
 * 403은 "권한은 없지만 그 자원은 존재한다"를 알려주는 셈이기 때문이다(api-spec D-4).
 */
@DisplayName("거래내역 인가 (F-06)")
class TransactionAuthorizationTest extends ApiTestSupport {

    private User me;
    private Category myCategory;
    private Category othersCategory;
    private Transaction othersTransaction;

    @BeforeEach
    void setUpFixtures() {
        me = createUser("me@moneylog.test");
        User other = createUser("other@moneylog.test");

        myCategory = createCategory(me, "식비", TransactionType.EXPENSE);
        othersCategory = createCategory(other, "식비", TransactionType.EXPENSE);
        othersTransaction = createTransaction(other, othersCategory, 10_000L);
    }

    @Test
    @DisplayName("남의 거래를 단건 조회하면 404를 준다")
    void findOne_othersTransaction_returns404() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", othersTransaction.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 거래를 수정하려 하면 404를 주고 원본은 그대로 남는다")
    void update_othersTransaction_returns404_andRecordUnchanged() throws Exception {
        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE, 999_999L, myCategory.getId(), "가로채기 시도", LocalDate.now());

        mockMvc.perform(put("/api/transactions/{id}", othersTransaction.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        Transaction survived = transactionRepository.findById(othersTransaction.getId()).orElseThrow();
        assertThat(survived.getAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("남의 거래를 삭제하려 하면 404를 주고 실제로 지워지지 않는다")
    void delete_othersTransaction_returns404_andRecordSurvives() throws Exception {
        mockMvc.perform(delete("/api/transactions/{id}", othersTransaction.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        assertThat(transactionRepository.findById(othersTransaction.getId())).isPresent();
    }

    @Test
    @DisplayName("목록 조회에는 남의 거래가 섞이지 않는다")
    void findAll_excludesOthersTransactions() throws Exception {
        Transaction mine = createTransaction(me, myCategory, 3_000L);

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions.length()").value(1))
                // JSON의 작은 정수는 Integer로 읽히므로 Long을 그대로 넣으면 equals가 false다.
                .andExpect(jsonPath("$.data.transactions[0].id").value(mine.getId().intValue()))
                .andExpect(jsonPath("$.meta.pagination.totalItems").value(1));
    }

    @Test
    @DisplayName("남의 카테고리로는 거래를 등록할 수 없다")
    void create_withOthersCategory_returns404() throws Exception {
        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE, 5_000L, othersCategory.getId(), "남의 카테고리", LocalDate.now());

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("월별 통계에 남의 금액이 합산되지 않는다")
    void monthlyStatistics_excludesOthersAmounts() throws Exception {
        createTransaction(me, myCategory, 3_000L);

        mockMvc.perform(get("/api/statistics/monthly")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expense").value(3_000))
                .andExpect(jsonPath("$.data.income").value(0))
                .andExpect(jsonPath("$.data.byCategory.length()").value(1))
                .andExpect(jsonPath("$.data.byCategory[0].total").value(3_000));
    }
}
