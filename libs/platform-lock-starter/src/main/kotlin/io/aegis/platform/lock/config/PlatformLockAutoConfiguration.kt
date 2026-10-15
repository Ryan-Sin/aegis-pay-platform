package io.aegis.platform.lock.config

import io.aegis.platform.lock.DistributedLockAspect
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnBean(RedissonClient::class)
class PlatformLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun distributedLockAspect(
        redissonClient: RedissonClient,
    ): DistributedLockAspect {
        return DistributedLockAspect(redissonClient)
    }
}