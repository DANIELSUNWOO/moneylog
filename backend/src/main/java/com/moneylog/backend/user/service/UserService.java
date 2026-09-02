package com.moneylog.backend.user.service;

import com.moneylog.backend.common.exception.BusinessException;
import com.moneylog.backend.user.dto.UserResponse;
import com.moneylog.backend.user.exception.UserErrorCode;
import com.moneylog.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** 토큰에는 id만 들어 있으므로, 사용자 정보가 필요한 이 엔드포인트에서만 실제로 조회한다. */
    @Transactional(readOnly = true)
    public UserResponse findMe(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
