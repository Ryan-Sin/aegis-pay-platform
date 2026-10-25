package io.aegis.services.auth.adapter.out.kms

import io.aegis.services.auth.application.port.out.JwtSignerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient

@Configuration(proxyBeanMethods = false)
class KmsConfiguration {

    @Bean(destroyMethod = "close")
    fun kmsClient(properties: KmsProperties): KmsClient =
        KmsClient.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentialsProvider(properties))
            .apply { properties.endpoint?.let(::endpointOverride) }
            .build()

    @Bean
    fun jwtSigner(kmsClient: KmsClient, properties: JwtSigningProperties): JwtSignerPort =
        KmsJwtSigner(kmsClient, properties)

    private fun credentialsProvider(properties: KmsProperties): AwsCredentialsProvider {
        val accessKeyId = properties.accessKeyId
        val secretAccessKey = properties.secretAccessKey
        if (accessKeyId.isNullOrBlank() || secretAccessKey.isNullOrBlank()) {
            return DefaultCredentialsProvider.builder().build()
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
    }
}
