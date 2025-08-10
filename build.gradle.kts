import org.gradle.internal.jvm.Jvm
import org.panteleyev.jpackage.ImageType
import org.panteleyev.jpackage.JPackageTask

plugins {
    java
    application
    id("org.panteleyev.jpackageplugin") version "1.7.3"
}

group = "ovh.paulem.mc"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.joml:joml:1.10.5")

    // BOM pour gérer les versions LWJGL
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.6"))

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
    mainClass.set(project.group.toString() + ".Main")
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

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

// --- JPACKAGE ---
val jvmOpts = listOf("-Dfile.encoding=UTF-8", "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED", "--add-opens=javafx.graphics/javafx.scene.layout=ALL-UNNAMED")

tasks.withType<JPackageTask>().configureEach {
    dependsOn("fatJar")

    appName = rootProject.name
    appVersion = project.version.toString()
    vendor = "Paulem"
    copyright = "Copyright (c) 2025 Paulem"
    runtimeImage = Jvm.current().javaHome
    destination = layout.projectDirectory.dir("dist")
    input = layout.buildDirectory.dir("libs")
    mainJar = (tasks.getByName("fatJar") as Jar).archiveFileName.get()
    mainClass = application.mainClass.get()
    javaOptions = jvmOpts
}

var infra = ""
tasks.register<JPackageTask>("zipjpackage") {
    group = tasks.jpackage.get().group

    type = ImageType.APP_IMAGE

    linux {
        infra = "linux"
    }

    mac {
        //icon = layout.projectDirectory.dir("src").dir("main").dir("resources").dir("assets").file("icons.icns")
        infra = "macos"
    }

    windows {
        //icon = layout.projectDirectory.dir("src").dir("main").dir("resources").dir("assets").file("icons.ico")

        winConsole = true
        infra = "windows"
    }

    finalizedBy("renameZip")
}

tasks.register<Zip>("renameZip") {
    group = tasks.jpackage.get().group
    archiveFileName.set(infra + "-FlowJsonCreator-" + project.version + ".zip")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))

    from(layout.projectDirectory.dir("dist/FlowJsonCreator"))
}

tasks.jpackage {
    linux {
        type = ImageType.DEB
    }

    mac {
        //icon = layout.projectDirectory.dir("src").dir("main").dir("resources").dir("assets").file("icons.icns")

        type = ImageType.DMG
    }

    windows {
        //icon = layout.projectDirectory.dir("src").dir("main").dir("resources").dir("assets").file("icons.ico")

        type = ImageType.MSI

        winConsole = true
        if(type.get() == ImageType.EXE || type.get() == ImageType.MSI) {
            winMenu = true
            winDirChooser = true
            winPerUserInstall = true
            winShortcut = true
            winShortcutPrompt = true
            // winUpdateUrl can be interesting for auto-updates
        }
    }
}

tasks.register<Delete>("deleteDist") {
    delete("dist")
}

tasks.clean {
    dependsOn("deleteDist")
}