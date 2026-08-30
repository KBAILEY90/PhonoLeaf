package com.phonoleaf.app

import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Wraps Google Play's own In-App Review API (BACKLOG.md section I) — shows
 * the native rate-this-app sheet without leaving PhonoLeaf or opening the
 * Play Store. Play's library decides internally whether to actually show
 * the sheet (it self-limits to a handful of times per year regardless of
 * how often this is called, and silently no-ops if the user already left a
 * review) — JS-side gating (see StoreReview in index.green.html) only
 * avoids calling this at every opportunity, not the real rate limit.
 */
@CapacitorPlugin(name = "StoreReview")
class StoreReviewPlugin : Plugin() {
    @PluginMethod
    fun requestReview(call: PluginCall) {
        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                call.reject("review flow request failed", task.exception)
                return@addOnCompleteListener
            }
            // launchReviewFlow's own task never reports whether the sheet
            // actually appeared or what the user did with it — that's
            // deliberate on Google's part (so an app can't retaliate
            // against a bad review) — so there is nothing meaningful to
            // resolve with beyond "the call completed."
            manager.launchReviewFlow(activity, task.result).addOnCompleteListener {
                call.resolve()
            }
        }
    }
}
