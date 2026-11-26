import groovy.json.JsonOutput
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

version = "1.0.0"

// The oldest supported Minecraft versions run on Java 21
val javaVersion = 21

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

// Build against the oldest JsMacrosCE release your extension intends to support
val jsmacrosVersion = "2.1.0"
val jsmacrosApi = "com.jsmacrosce.jsmacros:extension-api:$jsmacrosVersion"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven("https://maven.jsmacrosce.com/releases")
        }
        filter { includeModule("com.jsmacrosce.jsmacros", "extension-api") }
    }
}

// Bundle the engine and its runtime dependencies so users install just one extension JAR
val embedded = configurations.dependencyScope("embedded")
val embeddedRuntime = configurations.resolvable("embeddedRuntime") {
    extendsFrom(embedded.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion)
    }
}
configurations.compileOnly {
    extendsFrom(embedded.get())
}

dependencies {
    compileOnly(jsmacrosApi)
    // Language runtime
    add(embedded.name, "org.apache.commons:commons-jexl3:3.7.1")

    // Headless tests need libraries
    testImplementation(jsmacrosApi)
    testRuntimeOnly("com.google.code.gson:gson:2.14.0")
    testRuntimeOnly("com.google.guava:guava:33.3.1-jre")
    testRuntimeOnly("it.unimi.dsi:fastutil:8.5.15")
    testRuntimeOnly("org.javassist:javassist:3.30.2-GA")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

// Generate the descriptor from the bundled JARs so it stays in sync when dependencies change.
val dependencyDirectory = "META-INF/jsmacroscedeps"
// JsMacrosCE looks up this filename using the ID returned by getExtensionName().
val descriptor = layout.buildDirectory.file("generated/extension/jsmacrosce.ext.examplejexl.json")
val dependencyNames = embeddedRuntime.flatMap { it.elements }.map { archives ->
    archives.map { it.asFile.name }.sorted()
}
val generateExtensionDescriptor = tasks.register("generateExtensionDescriptor") {
    inputs.property("dependencyNames", dependencyNames)
    outputs.file(descriptor)
    doLast {
        val names = dependencyNames.get()
        check(names.all { it.endsWith(".jar") }) { "Expected only embedded JARs: $names" }
        check(names.toSet().size == names.size) { "Duplicate embedded JAR names: $names" }
        val paths = names.map { "$dependencyDirectory/$it" }
        val output = descriptor.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf("dependencies" to paths))) + "\n")
    }
}

tasks.processResources {
    from(generateExtensionDescriptor)
}

tasks.jar {
    from(embeddedRuntime) { into(dependencyDirectory) }
    from("LICENSE") { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // Keep timestamps and file order from changing the JAR between builds.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    useJUnitPlatform()
    // Keep extension classes off the test classpath so missing JAR entries fail the test
    classpath = sourceSets.test.get().output + configurations.testRuntimeClasspath.get()
    val extensionJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(extensionJar)
    inputs.file(layout.projectDirectory.file("examples/hello.jexl"))
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dexample.extensionJar=${extensionJar.get().asFile.absolutePath}")
    })
    // JsMacrosCE sizes its worker pool from the CPU count
    jvmArgs("-XX:ActiveProcessorCount=2")
}
