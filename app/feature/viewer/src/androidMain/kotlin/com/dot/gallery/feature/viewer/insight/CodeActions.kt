/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import com.dot.gallery.core.insight.CodePayload
import com.dot.gallery.core.insight.WifiSecurity
import com.dot.gallery.core.logging.logEvent
import com.dot.gallery.core.logging.printWarn
import java.util.Calendar
import java.util.TimeZone

private const val TAG = "insight.codes"

/**
 * Hands decoded values to other apps. Every function runs only from an explicit tap in the result
 * sheet; nothing here is called automatically. Each returns false when no app could take it.
 */
object CodeActions {

    fun openLink(context: Context, payload: CodePayload.Url): Boolean = start(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse(payload.url)).addCategory(Intent.CATEGORY_BROWSABLE),
        "link",
    )

    /**
     * Offers the network to the system. On Android 11+ the system shows its own "save network"
     * confirmation; older versions open the Wi-Fi panel, and the sheet copies the password first.
     */
    fun connectWifi(context: Context, payload: CodePayload.Wifi): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestion = buildSuggestion(payload)
            if (suggestion != null) {
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                if (start(context, intent, "wifi")) return true
            }
        }
        return start(context, Intent(Settings.Panel.ACTION_WIFI), "wifi-panel")
    }

    /** True when [connectWifi] can hand over the password itself (otherwise copy it first). */
    fun canSuggestWifi(payload: CodePayload.Wifi): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && payload.security != WifiSecurity.WEP

    private fun buildSuggestion(payload: CodePayload.Wifi): WifiNetworkSuggestion? = runCatching {
        val builder = WifiNetworkSuggestion.Builder().setSsid(payload.ssid).setIsHiddenSsid(payload.hidden)
        val password = payload.password
        when (payload.security) {
            WifiSecurity.OPEN -> Unit
            WifiSecurity.WPA -> if (password != null) builder.setWpa2Passphrase(password) else return null
            WifiSecurity.SAE -> if (password != null) builder.setWpa3Passphrase(password) else return null
            WifiSecurity.WEP -> return null
        }
        builder.build()
    }.onFailure {
        printWarn(TAG, "Wi-Fi suggestion rejected", ctx = mapOf("error" to it.toString()))
    }.getOrNull()

    fun addContact(context: Context, payload: CodePayload.Contact): Boolean {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
        payload.name?.let { intent.putExtra(ContactsContract.Intents.Insert.NAME, it) }
        payload.organization?.let { intent.putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        payload.phones.getOrNull(0)?.let { intent.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        payload.phones.getOrNull(1)?.let { intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
        payload.emails.getOrNull(0)?.let { intent.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        payload.emails.getOrNull(1)?.let { intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it) }
        payload.address?.let { intent.putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
        val notes = listOfNotNull(payload.note, payload.urls.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        if (notes.isNotEmpty()) intent.putExtra(ContactsContract.Intents.Insert.NOTES, notes.joinToString("\n"))
        return start(context, intent, "contact")
    }

    fun writeEmail(context: Context, payload: CodePayload.Email): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(payload.to))
            payload.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            payload.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
        return start(context, intent, "email")
    }

    /** Opens the dialer with the number filled in; never places the call. */
    fun dial(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), "dial")

    fun sendMessage(context: Context, payload: CodePayload.Sms): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", payload.number, null))
        payload.body?.let { intent.putExtra("sms_body", it) }
        return start(context, intent, "sms")
    }

    fun openMap(context: Context, payload: CodePayload.Geo): Boolean {
        val label = payload.query?.let { "(${Uri.encode(it)})" }.orEmpty()
        val uri = Uri.parse("geo:${payload.latitude},${payload.longitude}?q=${payload.latitude},${payload.longitude}$label")
        return start(context, Intent(Intent.ACTION_VIEW, uri), "map")
    }

    fun addEvent(context: Context, payload: CodePayload.Event): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        payload.summary?.let { intent.putExtra(CalendarContract.Events.TITLE, it) }
        payload.location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        payload.description?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        val start = payload.start?.let(::parseICalDate)
        start?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.millis) }
        start?.takeIf { it.allDay }?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true) }
        payload.end?.let(::parseICalDate)?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.millis) }
        return start(context, intent, "event")
    }

    fun share(context: Context, text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, null), "share")
    }

    /** Copies [text]; [sensitive] keeps it out of the clipboard preview (passwords). */
    fun copy(context: Context, text: String, sensitive: Boolean = false) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("", text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ClipDescription.EXTRA_IS_SENSITIVE
                    } else {
                        "android.content.extra.IS_SENSITIVE"
                    },
                    true,
                )
            }
        }
        clipboard.setPrimaryClip(clip)
        logEvent("insight.code.copy", mapOf("sensitive" to sensitive.toString()), scope = TAG)
    }

    private fun start(context: Context, intent: Intent, what: String): Boolean {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            logEvent("insight.code.action", mapOf("action" to what), scope = TAG)
            true
        } catch (e: ActivityNotFoundException) {
            printWarn(TAG, "No app for code action", ctx = mapOf("action" to what))
            false
        } catch (e: SecurityException) {
            printWarn(TAG, "Code action refused", ctx = mapOf("action" to what, "error" to e.toString()))
            false
        }
    }

    internal data class ICalDate(val millis: Long, val allDay: Boolean)

    /** Parses `yyyyMMdd`, `yyyyMMdd'T'HHmmss` and the UTC `...Z` form; null otherwise. */
    internal fun parseICalDate(value: String): ICalDate? {
        val v = value.trim()
        val digits = v.filter { it.isDigit() }
        if (digits.length < 8) return null
        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hasTime = v.contains('T') && digits.length >= 12
        val utc = v.endsWith("Z", ignoreCase = true)
        val calendar = Calendar.getInstance(if (utc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault())
        calendar.clear()
        if (hasTime) {
            val hour = digits.substring(8, 10).toInt()
            val minute = digits.substring(10, 12).toInt()
            val second = if (digits.length >= 14) digits.substring(12, 14).toInt() else 0
            calendar.set(year, month - 1, day, hour, minute, second)
        } else {
            calendar.set(year, month - 1, day)
        }
        return ICalDate(calendar.timeInMillis, allDay = !hasTime)
    }
}
