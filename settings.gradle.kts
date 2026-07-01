import java.io.File
import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "infinite-ricks-wallet"

fun locateAndroidSdk(root: File): File? {
    val localPropsFile = File(root, "local.properties")

    fun sdkFromLocalProps(): File? {
        if (!localPropsFile.exists()) {
            return null
        }
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        val path = props.getProperty("sdk.dir") ?: return null
        val dir = File(path.trim())
        return dir.takeIf { it.isDirectory }
    }

    sdkFromLocalProps()?.let { return it }

    val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (!env.isNullOrBlank()) {
        val dir = File(env.trim())
        if (dir.isDirectory) {
            localPropsFile.writeText("sdk.dir=${dir.invariantSeparatorsPath}\n")
            logger.lifecycle("Android SDK: local.properties criado a partir de ANDROID_HOME")
            return dir
        }
    }

    val home = System.getProperty("user.home")
    if (home != null) {
        val defaults = listOf(
            "$home/Android/Sdk",
            "/usr/lib/android-sdk",
            "/opt/android-sdk",
        )
        for (path in defaults) {
            val dir = File(path)
            if (dir.isDirectory) {
                localPropsFile.writeText("sdk.dir=${dir.invariantSeparatorsPath}\n")
                logger.lifecycle("Android SDK: local.properties criado (${dir.invariantSeparatorsPath})")
                return dir
            }
        }
    }

    return null
}

include(":rick-core")
include(":rick-api")
include(":rick-server")

if (locateAndroidSdk(settings.rootDir) != null) {
    include(":rick-android")
} else {
    logger.lifecycle(
        """
        
        [InfiniteRicks] Modulo :rick-android omitido — Android SDK nao encontrado.
          - :rick-core:test e :rick-server:build funcionam sem SDK
          - Para o APK: export ANDROID_HOME=/caminho/sdk
          - Ou: bash scripts/setup-android-sdk.sh
          - Veja local.properties.example e docs/INSTALLATION.md
        """.trimIndent()
    )
}
