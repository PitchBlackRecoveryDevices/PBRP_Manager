package com.pbrp.manager.utils

import android.util.Xml
import com.pbrp.manager.BuildInfo
import com.pbrp.manager.DeviceBuilds
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.regex.Pattern

sealed class SfResult {
    data class Found(val builds: DeviceBuilds) : SfResult()
    object Empty : SfResult()
    object NotFound : SfResult()
    object Error : SfResult()
}

object SourceForgeUtils {
    private val client = OkHttpClient()

    // Regex: PBRP-codename-4.0-20250531-1806-OFFICIAL.zip
    private val ZIP_META_PATTERN = Pattern.compile(
        "PBRP-.*?-(\\d+(?:\\.\\d+)+)-(\\d{8}).*?-(OFFICIAL|BETA|UNOFFICIAL).*?\\.zip", 
        Pattern.CASE_INSENSITIVE
    )

    fun checkAndFetch(codename: String): SfResult {
        val url = "https://sourceforge.net/projects/pbrp/rss?path=/$codename"
        
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()

            val response = client.newCall(request).execute()
            
            if (response.code == 404) {
                response.close()
                return SfResult.NotFound
            }

            if (!response.isSuccessful) {
                response.close()
                return SfResult.Error
            }

            val xmlData = response.body?.string() ?: return SfResult.Error
            val builds = parseRss(xmlData, codename)
            
            if (builds.isEmpty()) {
                return SfResult.Empty
            }

            val latest = builds.first()
            val older = if (builds.size > 1) builds.drop(1) else emptyList()

            return SfResult.Found(DeviceBuilds(latest, older))

        } catch (e: Exception) {
            e.printStackTrace()
            return SfResult.Error
        }
    }

    private fun parseRss(xml: String, codename: String): List<BuildInfo> {
        val builds = mutableListOf<BuildInfo>()
        val requiredPathPrefix = "/$codename/"

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""
            var rawTitle = "" 
            var pubDate = ""
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "item") insideItem = true
                    }
                    XmlPullParser.TEXT -> {
                        if (insideItem && currentTag.isNotEmpty()) {
                            val text = parser.text.trim()
                            when (currentTag) {
                                "title" -> rawTitle = text
                                "pubDate" -> pubDate = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (rawTitle.startsWith(requiredPathPrefix, ignoreCase = true)) {
                                val fileName = rawTitle.substringAfterLast('/')
                                val isImg = fileName.endsWith(".img", ignoreCase = true)
                                val isPbrpZip = fileName.startsWith("PBRP", ignoreCase = true) && fileName.endsWith(".zip", ignoreCase = true)

                                if (isImg || isPbrpZip) {
                                    val constructedLink = "https://sourceforge.net/projects/pbrp/files$rawTitle/download"
                                    builds.add(mapToBuildInfo(fileName, constructedLink, pubDate))
                                }
                            }
                            rawTitle = ""
                            pubDate = ""
                            insideItem = false
                        }
                        currentTag = "" 
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
        return builds
    }

    private fun mapToBuildInfo(fileName: String, link: String, rssPubDate: String): BuildInfo {
        val matcher = ZIP_META_PATTERN.matcher(fileName)
        
        var version = "Unknown"
        var date = rssPubDate
        var type = "SOURCEFORGE"

        if (matcher.find()) {
            version = matcher.group(1) ?: "Unknown"
            val rawDate = matcher.group(2)
            if (rawDate != null && rawDate.length == 8) {
                date = "${rawDate.substring(0, 4)}-${rawDate.substring(4, 6)}-${rawDate.substring(6, 8)}"
            }
            val tag = matcher.group(3)
            if (tag != null) {
                type = tag.uppercase()
            }
        } else {
            if (date.contains(",")) {
                try { date = date.substringBeforeLast(" ") } catch (e: Exception) {}
            }
            if (fileName.endsWith(".img", true)) {
                version = "Image"
                type = "IMG"
            }
        }

        return BuildInfo(
            version = version,
            buildType = type,
            date = date,
            downloadLink = link,
            githubLink = null,
            changelog = null,
            fileName = fileName 
        )
    }
}
