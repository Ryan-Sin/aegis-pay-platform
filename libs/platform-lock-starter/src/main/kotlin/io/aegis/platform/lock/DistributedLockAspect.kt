package io.aegis.platform.lock

import io.aegis.platform.core.exception.BusinessException
import io.aegis.platform.lock.exception.LockErrorCode
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory

@Aspect
class DistributedLockAspect(
    private val redissonClient: RedissonClient,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Around("@annotation(distributedLock)")
    fun lock(
        joinPoint: ProceedingJoinPoint,
        distributedLock: DistributedLock
    ): Any? {
        val signature = joinPoint.signature as MethodSignature
        val dynamicKey = CustomSpringELParser.getDynamicValue(
            parameterNames = signature.parameterNames,
            args = joinPoint.args,
            keyExpression = distributedLock.key,
        )
        val lockKey = "${distributedLock.prefix}$dynamicKey"
        val rLock = redissonClient.getLock(lockKey)

        val acquired = try {
            rLock.tryLock(
                distributedLock.waitTime,
                distributedLock.leaseTime,
                distributedLock.timeUnit
            )
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BusinessException(LockErrorCode.LOCK_INTERRUPTED, lockKey, cause = ex)
        }

        if (!acquired) {
            log.warn("분산 락 획득 실패 (타임아웃): key={}", lockKey)
            throw BusinessException(LockErrorCode.LOCK_ACQUISITION_FAILED, lockKey)
        }

        log.debug("분산 락 획득 성공: key={}", lockKey)
        return try {
            joinPoint.proceed()
        } finally {
            if (rLock.isLocked && rLock.isHeldByCurrentThread) {
                rLock.unlock()
                log.debug("분산 락 해제 완료 (DB 커밋 후): key={}", lockKey)
            }
        }
    }
}