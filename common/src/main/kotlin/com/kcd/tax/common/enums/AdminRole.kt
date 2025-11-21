package com.kcd.tax.common.enums

/**
 * 관리자 역할
 */
enum class AdminRole {
    /** 전체 권한 관리자 - 모든 사업장 조회 및 권한 관리 가능 */
    ADMIN,

    /** 제한 관리자 - 할당된 사업장만 조회 가능 */
    MANAGER
}
