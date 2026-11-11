package life.absinthe.pixelota

/** Centralized official Google hosts, catalog URLs, and QPR route builders. */
object OtaUrls {
    const val DEVELOPER_ANDROID_HOST = "developer.android.com"
    const val DEVELOPERS_GOOGLE_HOST = "developers.google.com"
    const val DL_GOOGLE_HOST = "dl.google.com"

    const val DEVELOPER_ANDROID_ORIGIN = "https://$DEVELOPER_ANDROID_HOST"
    const val DEVELOPERS_GOOGLE_ORIGIN = "https://$DEVELOPERS_GOOGLE_HOST"
    const val DL_GOOGLE_ORIGIN = "https://$DL_GOOGLE_HOST"
    private const val HTTPS_PREFIX = "https://"

    const val VERSIONS = "$DEVELOPER_ANDROID_ORIGIN/about/versions"
    const val STABLE = "$DEVELOPERS_GOOGLE_ORIGIN/android/ota"
    const val STABLE_LEGAL = "$STABLE#legal"

    fun developerPage(path: String): String = "$DEVELOPER_ANDROID_ORIGIN$path"

    fun version(major: Int): String = "$VERSIONS/$major"

    fun versionDownloadOta(major: Int): String = "$VERSIONS/$major/download-ota"

    fun versionDownloadOtaQpr(major: Int): String = "$VERSIONS/$major/download-ota-qpr"

    fun versionQprDownloadOta(major: Int, qpr: Int): String = "$VERSIONS/$major/qpr$qpr/download-ota"

    fun isOfficialPageHost(host: String?): Boolean =
        host == DEVELOPER_ANDROID_HOST || host == DEVELOPERS_GOOGLE_HOST

    fun displayUrl(url: String): String = url.removePrefix(HTTPS_PREFIX)
}
