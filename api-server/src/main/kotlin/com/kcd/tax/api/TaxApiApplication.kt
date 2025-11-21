package com.kcd.tax.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * 세금 TF - API 서버
 *
 * REST API 제공:
 * - 수집 요청 및 상태 조회
 * - 사업장 권한 관리 (ADMIN)
 * - 부가세 조회 (권한별 필터링)
 */
@SpringBootApplication(scanBasePackages = ["com.kcd.tax.api", "com.kcd.tax.infrastructure", "com.kcd.tax.common"])
@EntityScan(basePackages = ["com.kcd.tax.infrastructure.domain"])
@EnableJpaRepositories(basePackages = ["com.kcd.tax.infrastructure.repository"])
class TaxApiApplication

fun main(args: Array<String>) {
    runApplication<TaxApiApplication>(*args)
}
