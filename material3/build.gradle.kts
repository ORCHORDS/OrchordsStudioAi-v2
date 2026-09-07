plugins {
    id("orchordsai.android.library.compose")
}

android {
    namespace = "com.orchords.material3"
    // material-color-utilities is vendored under src/main/java (Apache-2.0, see
    // third-party/material-color-utilities/LICENSE and
    // THIRD_PARTY_NOTICES.md) so a plain `git clone` builds without any
    // submodule bootstrap step.
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
