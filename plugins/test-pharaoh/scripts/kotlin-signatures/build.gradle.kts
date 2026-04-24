plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.testpharaoh"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin compiler embeddable — gives us PSI to parse .kt files.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.testpharaoh.signatures.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("kotlin-signatures")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
