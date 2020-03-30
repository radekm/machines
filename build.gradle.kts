plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.70"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    jcenter()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // Nice we don't need kotlinx-coroutines.
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
