package com.moneylog.backend.transaction.repository;

import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.statistics.dto.CategorySumResponse;
import com.moneylog.backend.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** 단건 조회에도 항상 소유자 조건을 함께 건다. */
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    /** 카테고리 삭제 가능 여부 판정용 (erd.md D-2). */
    boolean existsByCategoryId(Long categoryId);

    /**
     * 목록 조회. 응답에 categoryName이 필요한데 category가 LAZY라 건마다 추가 쿼리(N+1)가 나간다.
     * @EntityGraph로 한 번에 같이 가져와 막는다.
     */
    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Transaction> findAll(org.springframework.data.jpa.domain.Specification<Transaction> spec, Pageable pageable);

    /** F-05 총수입/총지출. 전체를 읽어와 자바에서 더하지 않고 DB에서 집계한다. */
    @Query("""
            select t.type, sum(t.amount)
            from Transaction t
            where t.user.id = :userId
              and t.transactionDate between :start and :end
            group by t.type
            """)
    List<Object[]> sumAmountByType(@Param("userId") Long userId,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    /** F-05 카테고리별 지출 합계. 합계 내림차순. */
    @Query("""
            select new com.moneylog.backend.statistics.dto.CategorySumResponse(c.id, c.name, sum(t.amount))
            from Transaction t
            join t.category c
            where t.user.id = :userId
              and t.type = :type
              and t.transactionDate between :start and :end
            group by c.id, c.name
            order by sum(t.amount) desc
            """)
    List<CategorySumResponse> sumAmountByCategory(@Param("userId") Long userId,
                                                  @Param("type") TransactionType type,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);
}
