package io.aegis.services.auth.adapter.out.kms

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "aegis.auth.kms")
data class KmsProperties(
    val region: String = "ap-northeast-2",
    val endpoint: URI? = null,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
)
