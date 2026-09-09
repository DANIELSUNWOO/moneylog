package com.moneylog.backend.authorization;

import com.moneylog.backend.category.dto.CategoryRequest;
import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.support.ApiTestSupport;
import com.moneylog.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카테고리도 거래와 같은 인가 규칙을 따른다.
 * 카테고리가 뚫리면 거래가 직접 뚫리지 않아도 "남이 어떤 항목으로 돈을 쓰는지"가 새어나간다.
 */
@DisplayName("카테고리 인가 (F-06)")
class CategoryAuthorizationTest extends ApiTestSupport {

    private User me;
    private Category othersCategory;

    @BeforeEach
    void setUpFixtures() {
        me = createUser("me@moneylog.test");
        User other = createUser("other@moneylog.test");

        createCategory(me, "식비", TransactionType.EXPENSE);
        othersCategory = createCategory(other, "남의카테고리", TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("남의 카테고리를 수정하려 하면 404를 주고 원본 이름은 그대로다")
    void update_othersCategory_returns404_andNameUnchanged() throws Exception {
        CategoryRequest request = new CategoryRequest("바뀐이름", TransactionType.EXPENSE);

        mockMvc.perform(put("/api/categories/{id}", othersCategory.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        Category survived = categoryRepository.findById(othersCategory.getId()).orElseThrow();
        assertThat(survived.getName()).isEqualTo("남의카테고리");
    }

    @Test
    @DisplayName("남의 카테고리를 삭제하려 하면 404를 주고 실제로 지워지지 않는다")
    void delete_othersCategory_returns404_andSurvives() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", othersCategory.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        assertThat(categoryRepository.findById(othersCategory.getId())).isPresent();
    }

    @Test
    @DisplayName("목록 조회에는 내 카테고리만 나온다")
    void findAll_returnsOnlyMyCategories() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("식비"));
    }
}
