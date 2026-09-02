package com.moneylog.backend.statistics.dto;

/**
 * JPQL 생성자 표현식(new ...)으로 직접 채워지는 DTO.
 * categoryId까지 담는 이유: 차트 조각을 클릭해 해당 카테고리로 필터링하려면 id가 필요하다(api-spec D-13).
 */
public record CategorySumResponse(Long categoryId, String categoryName, Long total) {
}
