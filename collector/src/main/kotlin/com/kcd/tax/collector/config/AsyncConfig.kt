package com.kcd.tax.collector.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 비동기 처리 설정
 *
 * 데이터 수집기를 비동기로 실행하기 위한 ThreadPool 설정
 */
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 비동기 작업을 처리할 ThreadPool Executor 설정
     */
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()

        // 기본 스레드 수
        executor.corePoolSize = 5

        // 최대 스레드 수
        executor.maxPoolSize = 10

        // 큐 용량
        executor.queueCapacity = 100

        // 스레드 이름 접두사
        executor.threadNamePrefix = "tax-collector-"

        // 거부 정책: 호출한 스레드에서 실행
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())

        // 애플리케이션 종료 시 대기
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)

        executor.initialize()

        logger.info("AsyncConfig 초기화: corePoolSize=5, maxPoolSize=10, queueCapacity=100")

        return executor
    }

    /**
     * 비동기 작업 중 발생한 예외 처리
     */
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex, method, params ->
            logger.error(
                "비동기 작업 예외 발생: method=${method.name}, params=${params.contentToString()}",
                ex
            )
        }
    }
}
