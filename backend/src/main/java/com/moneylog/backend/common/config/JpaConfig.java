package com.moneylog.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseTimeEntity의 @CreatedDate/@LastModifiedDate를 동작시킨다. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
