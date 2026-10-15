rootProject.name = "aegis-pay-platform"

include(
    "libs:platform-core",
    "libs:platform-mvc-starter",
    "libs:platform-webflux-starter",
    "libs:platform-lock-starter",
    "libs:platform-http-client-starter",
    "libs:platform-observability-starter",
    "services:api-gateway",
    "services:auth-service",
    "services:payment-ledger-service",
    "services:realtime-broker-service",
    "simulators:terminal-simulator",
    "simulators:mock-van-server",
)
