package io.aegis.services.auth.support

import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

object ValkeyContainer {

    private const val IMAGE = "valkey/valkey:8.0-alpine"
    private const val PORT = 6379

    val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse(IMAGE))
            .withExposedPorts(PORT)
            .apply { start() }
    }

    val host: String
        get() = container.host

    val port: Int
        get() = container.getMappedPort(PORT)

    fun redisTemplate(): StringRedisTemplate {
        val connectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port))
            .apply { afterPropertiesSet() }
        return StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
    }
}
