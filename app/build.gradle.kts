plugins {
      id("com.android.application")
          id("org.jetbrains.kotlin.android")
}

android {
      namespace = "com.example.garminbridge"
      compileSdk = 34

      defaultConfig {
                applicationId = "com.example.garminbridge"
                minSdk = 26
                targetSdk = 34
                versionCode = 1
                versionName = "1.0"
      }

          buildTypes {
                    release {
                                  isMinifyEnabled = false
                    }
          }
              compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
              }
                  kotlinOptions {
                            jvmTarget = "17"
                  }
}

dependencies {
      implementation("androidx.core:core-ktx:1.12.0")
          implementation("androidx.appcompat:appcompat:1.6.1")
              implementation("com.google.android.material:material:1.11.0")

                  // WebView: für addDocumentStartJavaScript
                      implementation("androidx.webkit:webkit:1.10.0")

                          // VERIFY: Falls Gradle diese Version nicht findet, auf Google Maven
                              // nach "androidx.health.connect connect-client" die aktuelle nehmen.
                                  implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

                                      // Coroutines + Lifecycle
                                          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
                                              implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
