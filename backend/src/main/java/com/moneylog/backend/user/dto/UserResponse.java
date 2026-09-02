package com.moneylog.backend.user.dto;

import com.moneylog.backend.user.entity.User;

/** 비밀번호는 어떤 경우에도 응답에 포함하지 않는다. */
public record UserResponse(Long id, String email, String nickname) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
