import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

group = "com.github.jparound30"
version = "1.0-SNAPSHOT"

val encodingJvmArgs = listOf(
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
)

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.dotenv.kotlin)
    implementation(libs.koog.agents)
    implementation(libs.slf4j.simple)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.withType<JavaExec> {
    jvmArgs = encodingJvmArgs
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
tasks.javadoc {
    options.encoding = "UTF-8"
}

tasks.test {
    jvmArgs = encodingJvmArgs
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+MultiDollarInterpolation"))
}

tasks.jar.configure {
    manifest {
        attributes(mapOf("Main-Class" to "com.github.jparound30.MainKt"))
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}