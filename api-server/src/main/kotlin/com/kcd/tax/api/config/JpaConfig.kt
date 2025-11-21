package com.kcd.tax.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA 설정
 *
 * JPA Auditing 활성화 (createdAt, updatedAt 자동 관리)
 */
@Configuration
@EnableJpaAuditing
class JpaConfig
