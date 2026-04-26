plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

group = "dev.pipeline"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // OpenAI client (aallam)
    implementation("com.aallam.openai:openai-client:4.0.1")

    // Ktor engine required by openai-client
    implementation("io.ktor:ktor-client-okhttp:3.1.1")

    // LangChain4j – Ollama integration
    implementation("dev.langchain4j:langchain4j-ollama:1.0.0-beta3")
    implementation("dev.langchain4j:langchain4j:1.0.0-beta3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("dev.pipeline.cli.MainKt")
}
