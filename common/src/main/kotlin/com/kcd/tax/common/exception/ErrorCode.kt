package com.kcd.tax.common.exception

/**
 * 에러 코드 정의
 */
enum class ErrorCode(
    val code: String,
    val message: String
) {
    // 인증/인가 (AUTH)
    UNAUTHORIZED("AUTH001", "인증 정보가 없습니다."),
    INVALID_CREDENTIALS("AUTH002", "유효하지 않은 인증 정보입니다."),
    FORBIDDEN("AUTH003", "권한이 없습니다."),

    // 사업장 (BIZ)
    BUSINESS_NOT_FOUND("BIZ001", "사업장을 찾을 수 없습니다."),
    BUSINESS_ALREADY_EXISTS("BIZ002", "이미 존재하는 사업장입니다."),
    INVALID_BUSINESS_NUMBER("BIZ003", "유효하지 않은 사업자번호입니다."),

    // 수집 (COL)
    COLLECTION_ALREADY_IN_PROGRESS("COL001", "이미 수집이 진행 중입니다."),
    COLLECTION_NOT_COMPLETED("COL002", "수집이 완료되지 않았습니다."),
    COLLECTION_FAILED("COL003", "데이터 수집에 실패했습니다."),
    INVALID_COLLECTION_STATUS("COL004", "유효하지 않은 수집 상태입니다."),

    // 권한 (PER)
    PERMISSION_ALREADY_EXISTS("PER001", "이미 권한이 부여되었습니다."),
    PERMISSION_NOT_FOUND("PER002", "권한을 찾을 수 없습니다."),
    NO_PERMISSION("PER003", "해당 사업장에 대한 권한이 없습니다."),

    // 관리자 (ADM)
    ADMIN_NOT_FOUND("ADM001", "관리자를 찾을 수 없습니다."),
    ADMIN_ALREADY_EXISTS("ADM002", "이미 존재하는 관리자입니다."),

    // 검증 (VAL)
    INVALID_INPUT("VAL001", "유효하지 않은 입력값입니다."),
    REQUIRED_FIELD_MISSING("VAL002", "필수 입력값이 누락되었습니다."),

    // 시스템 (SYS)
    INTERNAL_SERVER_ERROR("SYS001", "서버 오류가 발생했습니다."),
    DATABASE_ERROR("SYS002", "데이터베이스 오류가 발생했습니다."),
    EXTERNAL_API_ERROR("SYS003", "외부 API 호출에 실패했습니다.")
}
