package com.kcd.tax.collector

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 세금 TF - 데이터 수집기
 *
 * 데이터 수집 작업 전담:
 * - DB 폴링으로 수집 대기 작업 확인
 * - 5분 수집 시뮬레이션
 * - 엑셀 데이터 파싱 및 DB 적재
 */
@SpringBootApplication(scanBasePackages = ["com.kcd.tax.collector", "com.kcd.tax.common"])
@EntityScan(basePackages = ["com.kcd.tax.infrastructure.domain"])
@EnableJpaRepositories(basePackages = ["com.kcd.tax.infrastructure.repository"])
@EnableScheduling
class CollectorApplication

fun main(args: Array<String>) {
    runApplication<CollectorApplication>(*args)
}
