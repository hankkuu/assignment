package com.kcd.tax.collector.scheduler

import com.kcd.tax.collector.service.CollectorService
import com.kcd.tax.common.constants.CollectionConstants
import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 수집 작업 폴링 스케줄러
 *
 * 주기적으로 DB를 폴링하여 NOT_REQUESTED 상태의 사업장을 찾아 수집 작업 수행
 */
@Component
class ScheduledCollectionPoller(
    private val businessPlaceRepository: BusinessPlaceRepository,
    private val collectorService: CollectorService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 10초마다 수집 대기 중인 작업 확인 및 실행
     */
    @Scheduled(fixedDelay = CollectionConstants.POLL_INTERVAL_MILLIS)
    fun pollAndCollect() {
        val pendingJobs = businessPlaceRepository
            .findByCollectionStatusAndCollectionRequestedAtIsNotNull(CollectionStatus.NOT_REQUESTED)

        if (pendingJobs.isNotEmpty()) {
            logger.info("=== 수집 대기 작업 발견: ${pendingJobs.size}개 ===")
            pendingJobs.forEach { job ->
                logger.info("수집 작업 시작: businessNumber=${job.businessNumber}")
                try {
                    collectorService.collectData(job.businessNumber)
                } catch (e: Exception) {
                    logger.error("수집 작업 실패: businessNumber=${job.businessNumber}", e)
                }
            }
        }
    }
}
