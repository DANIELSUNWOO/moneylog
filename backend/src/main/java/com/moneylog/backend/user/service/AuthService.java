package com.moneylog.backend.user.service;

import com.moneylog.backend.auth.security.JwtTokenProvider;
import com.moneylog.backend.category.entity.Category;
import com.moneylog.backend.category.repository.CategoryRepository;
import com.moneylog.backend.common.domain.TransactionType;
import com.moneylog.backend.common.exception.BusinessException;
import com.moneylog.backend.user.dto.LoginRequest;
import com.moneylog.backend.user.dto.LoginResponse;
import com.moneylog.backend.user.dto.SignupRequest;
import com.moneylog.backend.user.dto.UserResponse;
import com.moneylog.backend.user.entity.User;
import com.moneylog.backend.user.exception.UserErrorCode;
import com.moneylog.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 가입 직후 바로 거래를 등록할 수 있도록 넣어주는 기본 카테고리 (F-02). */
    private static final List<Map.Entry<String, TransactionType>> DEFAULT_CATEGORIES = List.of(
            Map.entry("식비", TransactionType.EXPENSE),
            Map.entry("교통", TransactionType.EXPENSE),
            Map.entry("주거", TransactionType.EXPENSE),
            Map.entry("문화", TransactionType.EXPENSE),
            Map.entry("급여", TransactionType.INCOME),
            Map.entry("용돈", TransactionType.INCOME));

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))  // BCrypt 해시
                .nickname(request.nickname())
                .build());

        seedDefaultCategories(user);

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 이메일이 없든 비밀번호가 틀리든 같은 예외를 던진다(계정 열거 방지, api-spec D-8).
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        return new LoginResponse(jwtTokenProvider.createAccessToken(user.getId()));
    }

    private void seedDefaultCategories(User user) {
        List<Category> categories = DEFAULT_CATEGORIES.stream()
                .map(e -> Category.builder().user(user).name(e.getKey()).type(e.getValue()).build())
                .toList();
        categoryRepository.saveAll(categories);
    }
}
