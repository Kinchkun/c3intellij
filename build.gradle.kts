import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jetbrains.grammarkit")
}

tasks.generateLexer {
    sourceFile.set(file("src/main/java/org/c3lang/intellij/C3.flex"))
    targetOutputDir.set(file("src/main/gen/org/c3lang/intellij/lexer"))
    purgeOldFiles.set(true)
}

// Never package a plugin whose tests fail.
tasks.buildPlugin {
    dependsOn(tasks.test)
}

idea {
    module {
        generatedSourceDirs.add(file("src/main/gen"))
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/gen", "src/main/java")
        }
    }
}

repositories {
    // A global ~/.gradle init script adds a project-level repository, which (under
    // Gradle's default PREFER_PROJECT mode) overrides the repositories declared in
    // settings.gradle.kts. Re-declare the IntelliJ Platform repositories here so the
    // local-IDE artifacts repository used by local(...) is available.
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Use a locally installed IDE (CLion is the recommended IDE for C3).
        // Override the location with -PclionPath=/path/to/IDE.app or the CLION_PATH env var.
        local(
            providers.gradleProperty("clionPath")
                .orElse(providers.environmentVariable("CLION_PATH"))
                .orElse("/Users/kinch/Applications/CLion.app")
        )
        // Native Debugging Support (LLDB/CIDR). Used only by the optional, CLion-gated
        // debugger integration (see cidrdebugger.xml); the base plugin stays compatible
        // with non-CLion IDEs because that integration is an optional dependency.
        bundledPlugins("com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
}
