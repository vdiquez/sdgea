plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":platform-kotlin"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
