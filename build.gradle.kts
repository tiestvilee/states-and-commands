import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val http4kVersion = "4.10.1.0"
val forkHandlesVersion = "1.10.3.0"

plugins {
    kotlin("jvm") version "1.4.21"
}

group = "me.vile01"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.http4k:http4k-bom:${http4kVersion}"))
    implementation(platform("dev.forkhandles:forkhandles-bom:${forkHandlesVersion}"))

    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-client-okhttp")
    implementation("org.http4k:http4k-format-jackson")
    implementation("org.http4k:http4k-server-undertow")
    implementation("org.http4k:http4k-cloudnative")
    implementation("dev.forkhandles:result4k")
    implementation("dev.forkhandles:values4k")

    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}