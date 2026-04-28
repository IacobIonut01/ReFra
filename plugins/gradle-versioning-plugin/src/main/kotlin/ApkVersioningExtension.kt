import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Configuration DSL for the `apk-versioning` plugin.
 *
 * ### Version code
 * Per-flavor version codes are computed as:
 * ```
 * computedVersionCode = flavorOffset + baseVersionCode * versionCodeMultiplier
 * ```
 * where `flavorOffset` comes from [flavorVersionCodes] and `baseVersionCode` from `defaultConfig.versionCode`.
 * Flavors not present in the map get offset `0`.
 *
 * ### Output filename
 * [outputFileName] is a template string with `{placeholder}` tokens.
 *
 * **Built-in placeholders:**
 * - `{versionName}` – `defaultConfig.versionName`
 * - `{versionCode}` – computed per-flavor version code
 * - `{flavorName}` – product flavor name
 * - `{buildType}` – build type name (debug / release / …)
 *
 * **Custom placeholders** are added via [variables]; any `{key}` in the
 * template is replaced with the corresponding value.
 *
 * ### Example
 * ```kotlin
 * apkVersioning {
 *     flavorVersionCodes.set(mapOf("arm64-v8a" to 4, "x86_64" to 2))
 *     versionCodeMultiplier.set(10)
 *     outputFileName.set("{appName}-{versionName}-{versionCode}{suffix}-{flavorName}-{buildType}")
 *     variables.put("appName", "MyApp")
 *     variables.put("suffix", "-full")
 * }
 * ```
 */
abstract class ApkVersioningExtension {
    /** Per-flavor version code offsets. */
    abstract val flavorVersionCodes: MapProperty<String, Int>

    /** Multiplier applied to `defaultConfig.versionCode` before adding the flavor offset. Default `10`. */
    abstract val versionCodeMultiplier: Property<Int>

    /**
     * Output APK filename template (without `.apk` extension).
     *
     * **Optional.** When not set, the default AGP naming is preserved.
     * Set this to enable APK renaming via the artifact transform pipeline.
     */
    abstract val outputFileName: Property<String>

    /** Custom template variables resolved in [outputFileName]. */
    abstract val variables: MapProperty<String, String>
}
