import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.mee42"
version = "v0.0.1"

plugins {
    java
    kotlin("jvm") version "1.3.40"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}


tasks.withType<Test> {
  useJUnitPlatform()
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.0.0-BETA3") // for kotest framework
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.0.0-BETA3") // for kotest core jvm assertions
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
                "Main-Class" to "dev.mee42.arg.MainKt"
        ))
    }
}