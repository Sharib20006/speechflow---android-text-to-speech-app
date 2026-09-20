import org.gradle.internal.os.OperatingSystem

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

val abis = (findProperty("evvdroid.abis") as String? ?: "arm64-v8a,armeabi-v7a")
	.split(",").map { it.trim() }.filter { it.isNotEmpty() }
val rulesForm = findProperty("evvdroid.rules") as String? ?: "c"
val languages = findProperty("evvdroid.langs") as String? ?: "lang/enus lang/engb lang/eses lang/esus lang/frfr lang/frca lang/dede lang/itit lang/plpl lang/jajp"
val nativeOut = layout.buildDirectory.dir("native/jniLibs")

// The engine is built by its own Makefile rather than by CMake, because that
// Makefile writes the language rules with Python before it compiles them and
// nothing here has a reason to reimplement that.
val buildNative by tasks.registering(Exec::class) {
	group = "build"
	description = "Cross-compiles the openevv engine and the JNI bridge for each ABI."
	val engine = rootProject.layout.projectDirectory.dir("native/openevv")
	val pyScript = rootProject.layout.projectDirectory.file("native/build_native_windows.py")
	inputs.dir(rootProject.layout.projectDirectory.dir("native/openevv/src"))
	inputs.dir(rootProject.layout.projectDirectory.dir("native/openevv/lang"))
	inputs.file(pyScript)
	inputs.file(layout.projectDirectory.file("src/main/cpp/evv_jni.c"))
	inputs.property("abis", abis)
	inputs.property("rules", rulesForm)
	inputs.property("langs", languages)
	outputs.dir(nativeOut)
	val py = if (OperatingSystem.current().isWindows) "python" else "python3"
	commandLine(py, pyScript.asFile.absolutePath)
	environment("ABIS", abis.joinToString(" "))
	environment("RULES", rulesForm)
	environment("LANGS", languages)
	environment("OUT", nativeOut.get().asFile.absolutePath)
	doFirst {
		if (!engine.file("Makefile").asFile.exists()) {
			throw GradleException("native/openevv is empty. Run: git submodule update --init --recursive")
		}
	}
}

android {
	namespace = "org.evvdroid"
	compileSdk = 36
	ndkVersion = "26.1.10909125"

	defaultConfig {
		applicationId = "in.visionHacks.eloquencePlus"
		minSdk = 23
		targetSdk = 36
		versionCode = 4
		versionName = "1.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		ndk {
			abiFilters += abis
		}
		resourceConfigurations += listOf("en")
	}

	dependenciesInfo {
		includeInApk = false
		includeInBundle = false
	}

	sourceSets.getByName("androidTest") {
		kotlin.srcDirs("src/androidTest/kotlin")
	}

	sourceSets.getByName("test") {
		kotlin.srcDirs("src/test/kotlin")
	}

	sourceSets.getByName("main") {
		kotlin.srcDirs("src/main/kotlin")
		jniLibs.srcDirs(nativeOut)
	}

	signingConfigs {
		create("release") {
			storeFile = file("keystore/release.jks")
			storePassword = "eloquencepro"
			keyAlias = "eloquence_pro"
			keyPassword = "eloquencepro"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			signingConfig = signingConfigs.getByName("release")
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			ndk {
				debugSymbolLevel = "NONE"
			}
		}
		debug {
			isJniDebuggable = true
		}
	}

	packaging {
		resources {
			excludes += listOf(
				"META-INF/*.version",
				"META-INF/*.kotlin_module",
				"META-INF/DEPENDENCIES",
				"META-INF/LICENSE*",
				"META-INF/NOTICE*",
				"META-INF/*.txt",
				"META-INF/INDEX.LIST",
				"DebugProbesKt.bin",
				"kotlin-tooling-metadata.json",
				"META-INF/AL2.0",
				"META-INF/LGPL2.1",
				"META-INF/com/android/build/gradle/app-metadata.properties",
				"META-INF/version-control-info.textproto",
				"META-INF/com.android.tools/r8/**"
			)
		}
		jniLibs {
			useLegacyPackaging = true
		}
	}

	splits {
		abi {
			isEnable = (findProperty("evvdroid.abiSplits") as String?)?.toBoolean() ?: true
			reset()
			include(*abis.toTypedArray())
			isUniversalApk = true
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	buildFeatures {
		buildConfig = true
		compose = true
	}
}

tasks.named("preBuild") {
	dependsOn(buildNative)
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.material)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.material3)

	testImplementation(libs.junit)
	androidTestImplementation(libs.junit)
	androidTestImplementation(libs.androidx.test.junit)
	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.core)
}
