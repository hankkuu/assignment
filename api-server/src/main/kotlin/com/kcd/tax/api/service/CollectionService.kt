package com.kcd.tax.api.service

import com.kcd.tax.common.enums.CollectionStatus
import com.kcd.tax.common.exception.ConflictException
import com.kcd.tax.common.exception.ErrorCode
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 수집 관리 서비스 (API Server)
 *
 * 수집 요청 및 상태 조회 기능 제공
 * 실제 수집은 Collector 모듈에서 DB polling으로 수행
 */
@Service
@Transactional(readOnly = true)
class CollectionService(
    private val businessPlaceRepository: BusinessPlaceRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 수집 요청
     *
     * 상태를 NOT_REQUESTED로 유지하여 Collector가 polling으로 가져갈 수 있도록 함
     *
     * @param businessNumber 사업자번호
     * @return 현재 수집 상태
     * @throws NotFoundException 사업장을 찾을 수 없는 경우
     * @throws ConflictException 이미 수집 중인 경우
     */
    @Transactional
    fun requestCollection(businessNumber: String): CollectionStatus {
        logger.info("수집 요청: businessNumber=$businessNumber")

        // 1. 사업장 존재 여부 확인
        val businessPlace = businessPlaceRepository.findById(businessNumber)
            .orElseThrow {
                NotFoundException(
                    ErrorCode.BUSINESS_NOT_FOUND,
                    "사업장을 찾을 수 없습니다: $businessNumber"
                )
            }

        // 2. 수집 상태 확인
        when (businessPlace.collectionStatus) {
            CollectionStatus.COLLECTING -> {
                throw ConflictException(
                    ErrorCode.COLLECTION_ALREADY_IN_PROGRESS,
                    "이미 수집이 진행 중입니다: $businessNumber"
                )
            }
            CollectionStatus.COLLECTED -> {
                throw ConflictException(
                    ErrorCode.COLLECTION_ALREADY_IN_PROGRESS,
                    "이미 수집이 완료되었습니다: $businessNumber"
                )
            }
            CollectionStatus.NOT_REQUESTED -> {
                // 정상적으로 수집 요청 가능
            }
        }

        // 3. 상태는 NOT_REQUESTED로 유지 (Collector가 polling으로 가져감)
        logger.info("수집 요청 접수 완료 - Collector가 처리 예정: businessNumber=$businessNumber")

        return businessPlace.collectionStatus
    }

    /**
     * 수집 상태 조회
     *
     * @param businessNumber 사업자번호
     * @return 현재 수집 상태
     * @throws NotFoundException 사업장을 찾을 수 없는 경우
     */
    fun getCollectionStatus(businessNumber: String): CollectionStatus {
        logger.debug("수집 상태 조회: businessNumber=$businessNumber")

        val businessPlace = businessPlaceRepository.findById(businessNumber)
            .orElseThrow {
                NotFoundException(
                    ErrorCode.BUSINESS_NOT_FOUND,
                    "사업장을 찾을 수 없습니다: $businessNumber"
                )
            }

        return businessPlace.collectionStatus
    }
}
