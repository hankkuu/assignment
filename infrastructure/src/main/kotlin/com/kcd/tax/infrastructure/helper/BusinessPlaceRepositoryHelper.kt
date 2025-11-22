package com.kcd.tax.infrastructure.helper

import com.kcd.tax.common.constants.ErrorMessages
import com.kcd.tax.common.exception.ErrorCode
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.repository.BusinessPlaceRepository
import org.springframework.stereotype.Component

/**
 * BusinessPlace Repository 헬퍼
 *
 * 중복되는 사업장 조회 로직을 통합하여 DRY 원칙 준수
 */
@Component
class BusinessPlaceRepositoryHelper(
    private val businessPlaceRepository: BusinessPlaceRepository
) {
    /**
     * 사업장 조회 (없으면 예외 발생)
     *
     * @param businessNumber 사업자번호
     * @return 사업장
     * @throws NotFoundException 사업장을 찾을 수 없는 경우
     */
    fun findByIdOrThrow(businessNumber: String): BusinessPlace {
        return businessPlaceRepository.findById(businessNumber)
            .orElseThrow {
                NotFoundException(
                    ErrorCode.BUSINESS_NOT_FOUND,
                    ErrorMessages.withParam(ErrorMessages.BUSINESS_NOT_FOUND, businessNumber)
                )
            }
    }

    /**
     * 사업장 존재 여부 확인 후 예외 발생
     *
     * @param businessNumber 사업자번호
     * @throws ConflictException 이미 존재하는 경우
     */
    fun throwIfExists(businessNumber: String) {
        if (businessPlaceRepository.existsById(businessNumber)) {
            throw com.kcd.tax.common.exception.ConflictException(
                ErrorCode.BUSINESS_ALREADY_EXISTS,
                ErrorMessages.withParam(ErrorMessages.BUSINESS_ALREADY_EXISTS, businessNumber)
            )
        }
    }

    /**
     * 모든 사업장 조회
     *
     * @return 전체 사업장 목록
     */
    fun findAll(): List<BusinessPlace> {
        return businessPlaceRepository.findAll()
    }

    /**
     * 여러 사업장 조회 (N+1 Query 방지)
     *
     * @param businessNumbers 사업자번호 목록
     * @return 사업장 목록
     */
    fun findAllByIds(businessNumbers: List<String>): List<BusinessPlace> {
        return businessPlaceRepository.findAllById(businessNumbers)
    }

    /**
     * 사업장 저장
     *
     * @param businessPlace 사업장
     * @return 저장된 사업장
     */
    fun save(businessPlace: BusinessPlace): BusinessPlace {
        return businessPlaceRepository.save(businessPlace)
    }
}
