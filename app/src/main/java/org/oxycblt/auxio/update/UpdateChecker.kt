/*
 * Copyright (c) 2026 Emi music Project
 * UpdateChecker.kt is part of Emi music.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.oxycblt.auxio.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R
import timber.log.Timber as L

/** Checks GitHub releases for installable Emi music APK updates. */
object UpdateChecker {
    suspend fun checkAndNotify(context: Context) {
        val appContext = context.applicationContext
        val result = check(appContext, force = false)
        if (result is Result.Available) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_NOTIFIED_TAG, null) != result.release.tag) {
                if (showUpdateNotification(appContext, result.release)) {
                    prefs.edit { putString(KEY_LAST_NOTIFIED_TAG, result.release.tag) }
                }
            }
        }
    }

    suspend fun check(context: Context, force: Boolean): Result =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!force && now - prefs.getLong(KEY_LAST_CHECK_MS, 0L) < CHECK_INTERVAL_MS) {
                return@withContext Result.Current
            }

            prefs.edit { putLong(KEY_LAST_CHECK_MS, now) }
            try {
                val release = fetchLatestRelease()
                if (isNewer(release.tag, BuildConfig.UPDATE_TAG)) {
                    Result.Available(release)
                } else {
                    Result.Current
                }
            } catch (e: Exception) {
                L.w(e, "Unable to check for Emi music updates")
                Result.Failed
            }
    }

    @SuppressLint("MissingPermission")
    fun showUpdateNotification(context: Context, release: Release): Boolean {
        if (!canPostNotifications(context)) {
            return false
        }

        val appContext = context.applicationContext
        val manager = NotificationManagerCompat.from(appContext)
        val channel =
            NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT,
                )
                .setName(appContext.getString(R.string.lbl_update_available))
                .setShowBadge(true)
                .build()
        manager.createNotificationChannel(channel)

        val installUri = (release.apkUrl ?: release.pageUrl).toUri()
        val intent =
            Intent(Intent.ACTION_VIEW, installUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                IntegerTable.UPDATE_NOTIFICATION_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val text = appContext.getString(R.string.lng_update_available, release.name)

        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_24)
                .setContentTitle(appContext.getString(R.string.lbl_update_available))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        manager.notify(IntegerTable.UPDATE_NOTIFICATION_CODE, notification)
        return true
    }

    private fun canPostNotifications(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun fetchLatestRelease(): Release {
        val connection =
            (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Emi-music/${BuildConfig.VERSION_NAME}")
            }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("GitHub release check failed with HTTP $responseCode")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }

            return Release(
                tag = json.getString("tag_name"),
                name = json.optString("name", json.getString("tag_name")),
                pageUrl = json.getString("html_url"),
                apkUrl = apkUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(latestTag: String, currentTag: String): Boolean {
        val latest = ParsedTag.from(latestTag) ?: return latestTag != currentTag
        val current =
            ParsedTag.from(currentTag)
                ?: ParsedTag.from("emi-music-v${BuildConfig.VERSION_NAME}-0")
                ?: return latestTag != currentTag
        val versionComparison = compareVersions(latest.version, current.version)
        return versionComparison > 0 ||
            (versionComparison == 0 && latest.buildNumber > current.buildNumber)
    }

    private fun compareVersions(first: List<Int>, second: List<Int>): Int {
        val length = maxOf(first.size, second.size)
        for (i in 0 until length) {
            val left = first.getOrElse(i) { 0 }
            val right = second.getOrElse(i) { 0 }
            if (left != right) {
                return left.compareTo(right)
            }
        }
        return 0
    }

    data class Release(
        val tag: String,
        val name: String,
        val pageUrl: String,
        val apkUrl: String?,
    )

    sealed interface Result {
        data class Available(val release: Release) : Result
        data object Current : Result
        data object Failed : Result
    }

    private data class ParsedTag(val version: List<Int>, val buildNumber: Int) {
        companion object {
            private val TAG_PATTERN = Regex("""emi-music-v([0-9]+(?:\.[0-9]+)*)(?:-([0-9]+))?.*""")

            fun from(tag: String): ParsedTag? {
                val match = TAG_PATTERN.matchEntire(tag) ?: return null
                val version = match.groupValues[1].split('.').mapNotNull { it.toIntOrNull() }
                if (version.isEmpty()) {
                    return null
                }
                val buildNumber = match.groupValues.getOrNull(2)?.toIntOrNull() ?: -1
                return ParsedTag(version, buildNumber)
            }
        }
    }

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/SHNWAZX/Emi-Music/releases/latest"
    private val PREFS_NAME = BuildConfig.APPLICATION_ID + ".updates"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private val CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.UPDATES"
}
