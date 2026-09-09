package com.moneylog.backend.transaction.service;

import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.category.exception.CategoryErrorCode;
import com.moneylog.backend.category.repository.CategoryRepository;
import com.moneylog.backend.common.domain.MonthRange;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.exception.BusinessException;
import com.moneylog.backend.transaction.dto.TransactionRequest;
import com.moneylog.backend.transaction.dto.TransactionResponse;
import com.moneylog.backend.transaction.entity.Transaction;
import com.moneylog.backend.transaction.exception.TransactionErrorCode;
import com.moneylog.backend.transaction.repository.TransactionRepository;
import com.moneylog.backend.user.entity.User;
import com.moneylog.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        // 연관관계의 FK만 채우면 되므로 실제 SELECT 없이 프록시 참조만 얻는다.
        User user = userRepository.getReferenceById(userId);
        Category category = getOwnedCategoryWithTypeCheck(userId, request);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .user(user)
                .category(category)
                .type(request.type())
                .amount(request.amount())
                .description(request.description())
                .transactionDate(request.transactionDate())
                .build());

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findOne(Long userId, Long transactionId) {
        return TransactionResponse.from(getOwnedTransaction(userId, transactionId));
    }

    /**
     * 목록 조회 (F-04). 필터가 선택적으로 조합되므로 파생 메서드 대신 Specification으로 짠다.
     * 파생 메서드로 하면 (기간) x (타입 유무) x (카테고리 유무) = 4가지 메서드가 필요해진다.
     */
    @Transactional(readOnly = true)
    public Page<Transaction> findAll(Long userId, String yearMonth, TransactionType type,
                                     Long categoryId, Pageable pageable) {
        MonthRange range = MonthRange.of(yearMonth);

        // 첫 조건이 소유자 필터다. 이게 빠지면 남의 거래가 섞인다.
        Specification<Transaction> spec =
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        spec = spec.and((root, query, cb) ->
                cb.between(root.<LocalDate>get("transactionDate"), range.start(), range.end()));
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }

        // 정렬 기준이 transactionDate 하나뿐이면 같은 날짜의 거래들 사이 순서가 보장되지 않는다.
        // DB가 매번 다른 순서로 돌려줄 수 있어 페이지 경계에서 항목이 중복되거나 누락된다.
        // id를 마지막 정렬 기준(tie-breaker)으로 붙여 순서를 확정한다.
        Pageable stableOrder = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Order.desc("id"))));

        return transactionRepository.findAll(spec, stableOrder);
    }

    @Transactional
    public TransactionResponse update(Long userId, Long transactionId, TransactionRequest request) {
        Transaction transaction = getOwnedTransaction(userId, transactionId);
        Category category = getOwnedCategoryWithTypeCheck(userId, request);

        transaction.update(category, request.type(), request.amount(),
                request.description(), request.transactionDate());

        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long userId, Long transactionId) {
        transactionRepository.delete(getOwnedTransaction(userId, transactionId));
    }

    /** 없는 거래든 남의 거래든 똑같이 404. 존재 여부를 흘리지 않는다(api-spec D-4). */
    private Transaction getOwnedTransaction(Long userId, Long transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND));
    }

    /**
     * erd.md D-1에서 "서비스에서 검증한다"고 정한 지점.
     * 이 검증이 빠지면 "식비(EXPENSE) 카테고리인데 INCOME으로 기록된 거래"가 생겨 통계가 조용히 깨진다.
     */
    private Category getOwnedCategoryWithTypeCheck(Long userId, TransactionRequest request) {
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        if (category.getType() != request.type()) {
            throw new BusinessException(TransactionErrorCode.CATEGORY_TYPE_MISMATCH);
        }
        return category;
    }
}
