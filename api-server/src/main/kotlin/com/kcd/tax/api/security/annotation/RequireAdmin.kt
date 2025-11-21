package com.kcd.tax.api.security.annotation

/**
 * ADMIN 권한이 필요한 API에 사용하는 어노테이션
 * Controller 메서드에 적용 가능
 *
 * @RequireAuth와 함께 사용해야 함
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAdmin
