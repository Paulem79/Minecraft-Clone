plugins {
    `java`
    application
}

group = "ovh.paulem"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.1"
val jomlVersion = "1.10.5"

dependencies {
    implementation("org.joml:joml:$jomlVersion")

    // BOM pour gérer les versions LWJGL
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    // Artefacts Java
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")

    // Natives Windows (DLL) nécessaires au runtime
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-windows")
}

application {
    // Correction du nom de la classe principale
    mainClass.set("com.example.mc.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Optionnel: fat jar. Attention: il ne packagera pas automatiquement les natives par plateforme.
tasks.register<Jar>("fatJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
    archiveClassifier.set("all")
}