package com.moneylog.backend.category.service;

import com.moneylog.backend.category.dto.CategoryRequest;
import com.moneylog.backend.category.dto.CategoryResponse;
import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.category.exception.CategoryErrorCode;
import com.moneylog.backend.category.repository.CategoryRepository;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.exception.BusinessException;
import com.moneylog.backend.transaction.repository.TransactionRepository;
import com.moneylog.backend.user.entity.User;
import com.moneylog.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(Long userId, TransactionType type) {
        List<Category> categories = (type == null)
                ? categoryRepository.findAllByUserIdOrderByIdAsc(userId)
                : categoryRepository.findAllByUserIdAndTypeOrderByIdAsc(userId, type);
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        validateNotDuplicated(userId, request.name(), request.type());

        // 연관관계의 FK만 채우면 되므로 실제 SELECT 없이 프록시 참조만 얻는다.
        User user = userRepository.getReferenceById(userId);

        Category category = categoryRepository.save(Category.builder()
                .user(user).name(request.name()).type(request.type()).build());

        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long categoryId, CategoryRequest request) {
        Category category = getOwnedCategory(userId, categoryId);

        boolean typeChanged = category.getType() != request.type();
        boolean nameChanged = !category.getName().equals(request.name());

        // erd.md D-1에서 transactions.type을 categories.type의 복제본으로 두기로 했고,
        // 그 대가로 "두 값은 항상 같다"는 불변식을 서비스가 직접 지키기로 했다.
        // TransactionService는 거래 등록·수정 시점에 이 검사를 하지만, 카테고리 타입이
        // 뒤늦게 바뀌는 경로를 막지 않으면 이미 쌓인 거래들은 옛 타입으로 남아
        // "지출 카테고리에 매달린 수입 거래"가 조용히 생기고 통계가 어긋난다.
        // 삭제를 막는 CATEGORY_IN_USE와 같은 이유다 — 거래를 옮긴 뒤에 바꾸게 한다.
        if (typeChanged && transactionRepository.existsByCategoryId(category.getId())) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_TYPE_CHANGE_NOT_ALLOWED);
        }

        if (nameChanged || typeChanged) {
            validateNotDuplicated(userId, request.name(), request.type());
        }

        category.update(request.name(), request.type());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        Category category = getOwnedCategory(userId, categoryId);

        // erd.md D-2: 거래가 달린 카테고리는 지우지 않는다. 사용자가 거래를 옮긴 뒤 삭제하게 한다.
        if (transactionRepository.existsByCategoryId(category.getId())) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_IN_USE);
        }

        categoryRepository.delete(category);
    }

    /** id만으로 찾지 않는다. 내 것이 아니면 존재 여부조차 알려주지 않기 위해 404를 던진다. */
    private Category getOwnedCategory(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    private void validateNotDuplicated(Long userId, String name, TransactionType type) {
        if (categoryRepository.existsByUserIdAndNameAndType(userId, name, type)) {
            throw new BusinessException(CategoryErrorCode.DUPLICATE_CATEGORY);
        }
    }
}
