package io.aegis.services.auth.support

import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.KeyUsageType
import java.nio.file.Path

object LocalStackKms {
    const val SIGNING_KEY_ALIAS = "alias/aegis-jwt-signing"
    const val SIGNING_KEY_ID = "00000000-0000-0000-0000-00000000a001"
    const val REGION = "ap-northeast-2"

    private const val IMAGE = "localstack/localstack:4.14.0"
    private const val INIT_SCRIPT = "10-create-jwt-signing-key.sh"

    private val INIT_DIR: Path = Path.of("../../infra/localstack/init/ready.d")

    val container: LocalStackContainer by lazy {
        LocalStackContainer(DockerImageName.parse(IMAGE))
            .withServices("kms")
            .withEnv("AWS_DEFAULT_REGION", REGION)
            .withCopyFileToContainer(
                MountableFile.forHostPath(INIT_DIR.resolve(INIT_SCRIPT), 0b111_101_101),
                "/etc/localstack/init/ready.d/$INIT_SCRIPT",
            )
            .waitingFor(
                Wait.forHttp("/_localstack/init/ready")
                    .forPort(4566)
                    .forResponsePredicate { it.replace(" ", "").contains("\"completed\":true") },
            )
            .apply { start() }
    }

    val endpoint: String
        get() = container.endpoint.toString()

    fun client(): KmsClient =
        KmsClient.builder()
            .endpointOverride(container.endpoint)
            .region(Region.of(REGION))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(container.accessKey, container.secretKey)),
            )
            .build()

    fun createKey(
        client: KmsClient,
        keySpec: KeySpec = KeySpec.RSA_2048,
        keyUsage: KeyUsageType = KeyUsageType.SIGN_VERIFY,
    ): String = client.createKey { it.keySpec(keySpec).keyUsage(keyUsage) }.keyMetadata().keyId()
}
