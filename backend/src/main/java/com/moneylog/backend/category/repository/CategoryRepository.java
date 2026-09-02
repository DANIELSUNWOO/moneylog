package com.moneylog.backend.category.repository;

import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.common.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByUserIdOrderByIdAsc(Long userId);

    List<Category> findAllByUserIdAndTypeOrderByIdAsc(Long userId, TransactionType type);

    /** 인가의 기본형: id만으로 찾지 않고 항상 userId를 함께 건다. */
    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameAndType(Long userId, String name, TransactionType type);
}
