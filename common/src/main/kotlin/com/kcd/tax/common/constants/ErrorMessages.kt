package com.kcd.tax.common.constants

/**
 * 에러 메시지 상수
 */
object ErrorMessages {
    // 사업장 관련
    const val BUSINESS_NOT_FOUND = "사업장을 찾을 수 없습니다"
    const val BUSINESS_ALREADY_EXISTS = "이미 존재하는 사업자번호입니다"

    // 관리자 관련
    const val ADMIN_NOT_FOUND = "관리자를 찾을 수 없습니다"

    // 권한 관련
    const val PERMISSION_NOT_FOUND = "권한을 찾을 수 없습니다"
    const val PERMISSION_ALREADY_EXISTS = "이미 권한이 부여되었습니다"
    const val PERMISSION_DENIED = "해당 사업장에 대한 권한이 없습니다"

    // 수집 관련
    const val COLLECTION_ALREADY_IN_PROGRESS = "이미 수집이 진행 중입니다"
    const val COLLECTION_NOT_ALLOWED = "수집을 시작할 수 없는 상태입니다"

    /**
     * 메시지에 파라미터 추가
     */
    fun withParam(message: String, param: Any): String = "$message: $param"

    /**
     * 메시지에 여러 파라미터 추가
     */
    fun withParams(message: String, vararg params: Pair<String, Any>): String {
        val paramString = params.joinToString(", ") { "${it.first}=${it.second}" }
        return "$message: $paramString"
    }
}
