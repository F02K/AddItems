plugins {
    java
}

group = "dev.fosk"
version = "0.1.0-SNAPSHOT"

// Single source of truth for the targeted Minecraft version (see docs/minecraft-versions.md).
val minecraftVersion = "26.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    // Paper bundles Gson, so no extra JSON dependency is needed.
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion.build.+")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:deprecation")
    }
    processResources {
        val props = mapOf("version" to project.version, "apiVersion" to minecraftVersion)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    test {
        useJUnitPlatform()
    }
}

// --- Web dashboard (Svelte + Vite in /web) is bundled into the plugin jar ---
val npm = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"

val webInstall = tasks.register<Exec>("webInstall") {
    workingDir = file("web")
    commandLine(npm, "install")
    inputs.files("web/package.json", "web/package-lock.json").optional()
    outputs.dir("web/node_modules")
}

val webBuild = tasks.register<Exec>("webBuild") {
    dependsOn(webInstall)
    workingDir = file("web")
    commandLine(npm, "run", "build")
    inputs.dir("web/src")
    inputs.files("web/index.html", "web/vite.config.js", "web/package.json")
    outputs.dir(layout.buildDirectory.dir("web-dist"))
}

tasks.processResources {
    from(webBuild) { into("web") }
}

// `./gradlew deploy` rebuilds and copies the jar into the local test server (run/ is git-ignored).
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into("run/plugins")
    rename { "AddItems.jar" }
}

// Unit tests of the dashboard's pure logic (graph model, layout) run as part of `./gradlew check`.
val webTest = tasks.register<Exec>("webTest") {
    dependsOn(webInstall)
    workingDir = file("web")
    commandLine(npm, "test")
    inputs.dir("web/src")
    inputs.files("web/package.json")
    outputs.upToDateWhen { false }
}
tasks.named("check") { dependsOn(webTest) }
