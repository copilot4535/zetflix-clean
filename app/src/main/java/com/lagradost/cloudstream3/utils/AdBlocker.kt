package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Log

object AdBlocker {
    private val blockedDomains = hashSetOf(
        "ads.",
        "doubleclick",
        "popads",
        "zeroredirect",
        "cncverse",
        "adservice",
        "adsystem",
        "adtrack",
        "clickbank",
        "juicyads",
        "exoclick",
        "propellerads",
        "popcash",
        "ad-maven",
        "a-ads",
        "adform",
        "adnxs",
        "adroll",
        "adtech",
        "advertising.com",
        "amazon-adsystem",
        "bidswitch",
        "casalemedia",
        "criteo",
        "dotomi",
        "everesttech",
        "facebook.com/tr",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "ib.adnxs.com",
        "mathtag.com",
        "moatads.com",
        "openx.net",
        "pubmatic.com",
        "quantserve.com",
        "rubiconproject.com",
        "scorecardresearch.com",
        "serving-sys.com",
        "smartadserver.com",
        "taboola.com",
        "tapad.com",
        "yieldmo.com"
    )

    fun isAd(url: String?): Boolean {
        if (url == null) return false
        val lowerUrl = url.lowercase()
        
        // Basic check for common ad patterns
        if (blockedDomains.any { lowerUrl.contains(it) }) return true
        
        // Check for ad-like query parameters
        if (lowerUrl.contains("?ad=") || lowerUrl.contains("&ad=") || 
            lowerUrl.contains("utm_source=ad") || 
            lowerUrl.contains("affiliate") ||
            (lowerUrl.contains("/ad/") && !lowerUrl.contains("download"))
        ) return true
        
        return false
    }

    fun isBlocked(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.dataString
            if (isAd(data)) {
                Log.d("AdBlocker", "Blocked ad intent: $data")
                return true
            }
        }
        return false
    }

    /**
     * A restricted context passed to plugins to prevent malicious actions like redirects to ads.
     */
    class SafeContext(base: Context) : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) {
            if (isBlocked(intent)) return
            super.startActivity(intent)
        }

        override fun startActivity(intent: Intent?, options: Bundle?) {
            if (isBlocked(intent)) return
            super.startActivity(intent, options)
        }

        override fun startActivities(intents: Array<out Intent>?) {
            val filtered = intents?.filter { !isBlocked(it) }?.toTypedArray()
            if (filtered.isNullOrEmpty()) return
            super.startActivities(filtered)
        }

        override fun startActivities(intents: Array<out Intent>?, options: Bundle?) {
            val filtered = intents?.filter { !isBlocked(it) }?.toTypedArray()
            if (filtered.isNullOrEmpty()) return
            super.startActivities(filtered, options)
        }
    }
}
