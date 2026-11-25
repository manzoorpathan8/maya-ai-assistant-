plugins {
 id("com.android.application") version "8.5.2"
 id("org.jetbrains.kotlin.android") version "1.9.24"
}
android {
 namespace = "com.maya.assistant"
 compileSdk = 34
 defaultConfig { applicationId = "com.maya.assistant"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1" }
 buildFeatures { compose = true }
 composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 testImplementation("junit:junit:4.13.2")
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3:1.2.1")
 implementation("androidx.compose.material:material-icons-extended:1.6.8")
}
