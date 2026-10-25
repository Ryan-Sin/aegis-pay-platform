package io.aegis.services.auth.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Path

@TestConfiguration(proxyBeanMethods = false)
class MySqlContainerConfiguration {
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer =
        MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("aegis_auth")
            .withUsername("aegis")
            .withPassword("aegis_password")
            .apply {
                INIT_SCRIPTS.forEach { script ->
                    withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_DIR.resolve(script)),
                        "/docker-entrypoint-initdb.d/$script",
                    )
                }
            }

    private companion object {
        const val MYSQL_IMAGE = "mysql:8.4"

        val INIT_DIR: Path = Path.of("../../infra/mysql/init")
        val INIT_SCRIPTS = listOf("01-create-databases.sql", "02-aegis-auth-schema.sql")
    }
}
