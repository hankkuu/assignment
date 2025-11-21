package com.kcd.tax.api.security.annotation

/**
 * 인증이 필요한 API에 사용하는 어노테이션
 * Controller 클래스 또는 메서드에 적용 가능
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAuth
