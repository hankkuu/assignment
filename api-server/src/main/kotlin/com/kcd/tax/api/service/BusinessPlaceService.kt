package com.kcd.tax.api.service

import com.kcd.tax.common.constants.ErrorMessages
import com.kcd.tax.common.exception.ConflictException
import com.kcd.tax.common.exception.ErrorCode
import com.kcd.tax.common.exception.NotFoundException
import com.kcd.tax.infrastructure.domain.Admin
import com.kcd.tax.infrastructure.domain.BusinessPlace
import com.kcd.tax.infrastructure.domain.BusinessPlaceAdmin
import com.kcd.tax.infrastructure.helper.BusinessPlaceRepositoryHelper
import com.kcd.tax.infrastructure.repository.AdminRepository
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminRepository
import com.kcd.tax.infrastructure.repository.BusinessPlaceAdminDetail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 사업장 관리 및 권한 관리 서비스
 *
 * 사업장 CRUD 및 사업장-관리자 권한 관리 기능 제공
 * 리팩토링: 중복 코드 제거, 상수 사용, 헬퍼 활용
 */
@Service
@Transactional(readOnly = true)
class BusinessPlaceService(
    private val businessPlaceHelper: BusinessPlaceRepositoryHelper,
    private val adminRepository: AdminRepository,
    private val businessPlaceAdminRepository: BusinessPlaceAdminRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ========================================
    // 사업장 CRUD
    // ========================================

    /**
     * 사업장 생성
     *
     * @param businessNumber 사업자번호
     * @param name 사업장명
     * @return 생성된 사업장
     * @throws ConflictException 이미 존재하는 사업자번호인 경우
     */
    @Transactional
    fun createBusinessPlace(businessNumber: String, name: String): BusinessPlace {
        logger.info("사업장 생성 요청: businessNumber=$businessNumber, name=$name")

        // 중복 확인
        businessPlaceHelper.throwIfExists(businessNumber)

        val businessPlace = BusinessPlace(
            businessNumber = businessNumber,
            name = name
        )

        val saved = businessPlaceHelper.save(businessPlace)
        logger.info("사업장 생성 완료: businessNumber=${saved.businessNumber}")

        return saved
    }

    /**
     * 사업장 목록 조회
     *
     * @return 전체 사업장 목록
     */
    fun getAllBusinessPlaces(): List<BusinessPlace> {
        logger.debug("사업장 목록 조회")
        return businessPlaceHelper.findAll()
    }

    /**
     * 사업장 상세 조회
     *
     * @param businessNumber 사업자번호
     * @return 사업장 정보
     * @throws NotFoundException 사업장을 찾을 수 없는 경우
     */
    fun getBusinessPlace(businessNumber: String): BusinessPlace {
        logger.debug("사업장 조회: businessNumber=$businessNumber")
        return businessPlaceHelper.findByIdOrThrow(businessNumber)
    }

    /**
     * 사업장 정보 수정
     *
     * @param businessNumber 사업자번호
     * @param name 변경할 사업장명
     * @return 수정된 사업장
     * @throws NotFoundException 사업장을 찾을 수 없는 경우
     */
    @Transactional
    fun updateBusinessPlace(businessNumber: String, name: String): BusinessPlace {
        logger.info("사업장 수정 요청: businessNumber=$businessNumber, name=$name")

        val businessPlace = businessPlaceHelper.findByIdOrThrow(businessNumber)
        businessPlace.name = name
        val updated = businessPlaceHelper.save(businessPlace)

        logger.info("사업장 수정 완료: businessNumber=${updated.businessNumber}")

        return updated
    }

    // ========================================
    // 사업장 권한 관리
    // ========================================

    /**
     * 권한 정보 DTO
     */
    data class PermissionInfo(
        val id: Long?,
        val businessNumber: String,
        val adminId: Long,
        val adminUsername: String,
        val adminRole: String,
        val grantedAt: LocalDateTime
    )

    /**
     * 사업장에 관리자 권한 부여
     *
     * @param businessNumber 사업자번호
     * @param adminId 관리자 ID
     * @return 부여된 권한 정보
     * @throws NotFoundException 사업장 또는 관리자를 찾을 수 없는 경우
     * @throws ConflictException 이미 권한이 부여된 경우
     */
    @Transactional
    fun grantPermission(businessNumber: String, adminId: Long): PermissionInfo {
        logger.info("권한 부여 요청: businessNumber=$businessNumber, adminId=$adminId")

        // 1. 사업장 존재 확인
        businessPlaceHelper.findByIdOrThrow(businessNumber)

        // 2. 관리자 존재 확인
        val admin = findAdminOrThrow(adminId)

        // 3. 이미 권한이 부여되었는지 확인
        throwIfPermissionExists(businessNumber, adminId)

        // 4. 권한 부여
        val saved = createAndSavePermission(businessNumber, adminId)
        logger.info("권한 부여 완료: id=${saved.id}, businessNumber=$businessNumber, adminId=$adminId")

        return toPermissionInfo(saved, admin)
    }

    /**
     * 사업장의 관리자 권한 목록 조회 (N+1 Query 방지)
     *
     * @param businessNumber 사업자번호
     * @return 권한 목록
     */
    fun getPermissionsByBusinessNumber(businessNumber: String): List<PermissionInfo> {
        logger.debug("사업장 권한 목록 조회: businessNumber=$businessNumber")

        // 사업장 존재 확인
        businessPlaceHelper.findByIdOrThrow(businessNumber)

        // JOIN 쿼리로 한 번에 조회 (N+1 Query 방지)
        val details = businessPlaceAdminRepository.findDetailsByBusinessNumber(businessNumber)

        return details.map { detail ->
            PermissionInfo(
                id = detail.permissionId,
                businessNumber = detail.businessNumber,
                adminId = detail.adminId,
                adminUsername = detail.adminName,
                adminRole = detail.adminRole,
                grantedAt = detail.grantedAt
            )
        }
    }

    /**
     * 사업장의 관리자 권한 삭제
     *
     * @param businessNumber 사업자번호
     * @param adminId 관리자 ID
     * @throws NotFoundException 권한을 찾을 수 없는 경우
     */
    @Transactional
    fun revokePermission(businessNumber: String, adminId: Long) {
        logger.info("권한 삭제 요청: businessNumber=$businessNumber, adminId=$adminId")

        // 권한 존재 확인
        throwIfPermissionNotExists(businessNumber, adminId)

        val deletedCount = businessPlaceAdminRepository.deleteByBusinessNumberAndAdminId(
            businessNumber,
            adminId
        )

        logger.info("권한 삭제 완료: businessNumber=$businessNumber, adminId=$adminId, count=$deletedCount")
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * 관리자 조회 (없으면 예외)
     */
    private fun findAdminOrThrow(adminId: Long): Admin {
        return adminRepository.findById(adminId)
            .orElseThrow {
                NotFoundException(
                    ErrorCode.ADMIN_NOT_FOUND,
                    ErrorMessages.withParam(ErrorMessages.ADMIN_NOT_FOUND, adminId)
                )
            }
    }

    /**
     * 권한이 이미 존재하면 예외 발생
     */
    private fun throwIfPermissionExists(businessNumber: String, adminId: Long) {
        if (businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId)) {
            throw ConflictException(
                ErrorCode.PERMISSION_ALREADY_EXISTS,
                ErrorMessages.withParams(
                    ErrorMessages.PERMISSION_ALREADY_EXISTS,
                    "businessNumber" to businessNumber,
                    "adminId" to adminId
                )
            )
        }
    }

    /**
     * 권한이 존재하지 않으면 예외 발생
     */
    private fun throwIfPermissionNotExists(businessNumber: String, adminId: Long) {
        if (!businessPlaceAdminRepository.existsByBusinessNumberAndAdminId(businessNumber, adminId)) {
            throw NotFoundException(
                ErrorCode.PERMISSION_NOT_FOUND,
                ErrorMessages.withParams(
                    ErrorMessages.PERMISSION_NOT_FOUND,
                    "businessNumber" to businessNumber,
                    "adminId" to adminId
                )
            )
        }
    }

    /**
     * 권한 생성 및 저장
     */
    private fun createAndSavePermission(businessNumber: String, adminId: Long): BusinessPlaceAdmin {
        val permission = BusinessPlaceAdmin(
            businessNumber = businessNumber,
            adminId = adminId
        )
        return businessPlaceAdminRepository.save(permission)
    }

    /**
     * 권한 엔티티를 DTO로 변환
     */
    private fun toPermissionInfo(permission: BusinessPlaceAdmin, admin: Admin): PermissionInfo {
        return PermissionInfo(
            id = permission.id,
            businessNumber = permission.businessNumber,
            adminId = requireNotNull(admin.id) { "Admin ID는 필수입니다" },
            adminUsername = admin.username,
            adminRole = admin.role.name,
            grantedAt = permission.grantedAt
        )
    }
}
