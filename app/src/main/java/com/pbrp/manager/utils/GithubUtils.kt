package com.pbrp.manager.utils

import com.pbrp.manager.BuildInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

object GithubUtils {
    private val client = OkHttpClient()

    // Regex to parse: https://github.com/{owner}/{repo}/releases/tag/{tag}
    private val GITHUB_RELEASE_PATTERN = Pattern.compile(
        "https://github\\.com/([^/]+)/([^/]+)/releases/tag/([^/]+)/?",
        Pattern.CASE_INSENSITIVE
    )

    // Removed 'api' parameter as we use internal OkHttp now
    fun resolveBuild(build: BuildInfo): BuildInfo {
        val releaseUrl = build.githubLink ?: return build

        // 1. Convert HTML URL to API URL
        val apiUrl = convertToApiUrl(releaseUrl) ?: return build

        return try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "PBRP-Manager")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                // Log failure code (e.g. 403, 404)
                println("GitHub API Error: ${response.code} for $apiUrl")
                response.close()
                return build
            }

            val jsonData = response.body?.string()
            response.close()

            if (jsonData == null) return build

            // 2. Parse JSON
            val json = JSONObject(jsonData)
            val assets = json.getJSONArray("assets")

            var bestUrl: String? = null
            var bestName: String? = null

            // 3. Find Best Asset (Priority: Zip -> Img)
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                val downloadUrl = asset.getString("browser_download_url")

                // Prefer ZIP
                if (name.endsWith(".zip", true)) {
                    bestUrl = downloadUrl
                    bestName = name
                    break // Found priority, stop searching
                }
                
                // Fallback to IMG if we haven't found a zip yet
                if (name.endsWith(".img", true) && bestUrl == null) {
                    bestUrl = downloadUrl
                    bestName = name
                }
            }

            // 4. Update BuildInfo if asset found
            if (bestUrl != null && bestName != null) {
                build.copy(
                    downloadLink = bestUrl,
                    fileName = bestName
                )
            } else {
                build
            }

        } catch (e: Exception) {
            e.printStackTrace()
            build
        }
    }

    private fun convertToApiUrl(htmlUrl: String): String? {
        val matcher = GITHUB_RELEASE_PATTERN.matcher(htmlUrl)
        if (matcher.find()) {
            val owner = matcher.group(1)
            val repo = matcher.group(2)
            val tag = matcher.group(3)?.trim('/')
            return "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
        }
        return null
    }
}
