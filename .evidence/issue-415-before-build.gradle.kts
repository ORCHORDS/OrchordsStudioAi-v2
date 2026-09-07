plugins {
    id("orchordsai.android.library.compose")
}

android {
    namespace = "com.orchords.material3"
    sourceSets {
        named("main") {
            // Guarded by configuration-time check; CI runs `git submodule update --init --recursive`
            // before invoking Gradle (see CONTRIBUTING.md, docs/BUILDING.md).
            val mcuDir = file("material-color-utilities")
            check(mcuDir.isDirectory && mcuDir.resolve("kotlin/dynamiccolor/DynamicScheme.kt").isFile) {
                "material-color-utilities submodule is not initialised.\n" +
                    "Run from the repository root:\n" +
                    "    git submodule update --init --recursive\n" +
                    "or clone with --recurse-submodules:\n" +
                    "    git clone --recurse-submodules https://github.com/ORCHORDS/OrchordsStudioAi.git\n" +
                    "Expected file: ${mcuDir.resolve("kotlin/dynamiccolor/DynamicScheme.kt")}"
            }
            kotlin.srcDir("material-color-utilities/kotlin")
        }
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        check(requested.name != "material-color-utilities") {
            "material-color-utilities must come only from the pinned Git submodule"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
