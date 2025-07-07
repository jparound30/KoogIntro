plugins {
    kotlin("jvm") version "2.1.21"
}

group = "com.github.jparound30"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.dotenv.kotlin)
    implementation(libs.koog.agents)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}