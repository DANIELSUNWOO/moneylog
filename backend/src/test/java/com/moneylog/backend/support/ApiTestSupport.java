package com.moneylog.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneylog.backend.auth.security.JwtTokenProvider;
import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.category.repository.CategoryRepository;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.transaction.entity.Transaction;
import com.moneylog.backend.transaction.repository.TransactionRepository;
import com.moneylog.backend.user.entity.User;
import com.moneylog.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

/**
 * API 테스트 공통 준비물.
 *
 * 로그인 API를 거치지 않고 JwtTokenProvider로 토큰을 직접 만든다.
 * 여기서 검증하려는 것은 "로그인이 되는가"가 아니라 "로그인한 사용자가 남의 데이터에
 * 손댈 수 있는가"이므로, 준비 과정이 짧을수록 테스트의 의도가 선명해진다.
 *
 * 트랜잭션 롤백 대신 매 테스트 전에 직접 지우는 이유:
 * 테스트에 @Transactional을 붙이면 컨트롤러·서비스가 테스트와 같은 트랜잭션에 참여해서
 * 실제 운영과 다른 조건(항상 열려 있는 영속성 컨텍스트)에서 검증하게 된다.
 * 이 프로젝트는 open-in-view=false라 그 차이가 실제로 의미가 있으므로, 커밋된 상태에서 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    /** 외래키 순서대로 지운다: 거래 → 카테고리 → 사용자. */
    @BeforeEach
    void clearDatabase() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password("bcrypt-hash-placeholder")
                .nickname("tester")
                .build());
    }

    protected Category createCategory(User owner, String name, TransactionType type) {
        return categoryRepository.save(Category.builder()
                .user(owner)
                .name(name)
                .type(type)
                .build());
    }

    protected Transaction createTransaction(User owner, Category category, long amount) {
        return transactionRepository.save(Transaction.builder()
                .user(owner)
                .category(category)
                .type(category.getType())
                .amount(amount)
                .description("테스트 거래")
                .transactionDate(LocalDate.now())
                .build());
    }

    /** Authorization 헤더에 그대로 넣는 "Bearer xxx" 문자열. */
    protected String bearerOf(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }
}
