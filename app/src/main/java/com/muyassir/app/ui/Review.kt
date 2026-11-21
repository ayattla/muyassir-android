package com.muyassir.app.ui

import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/** Asks Google Play for its in-app rating sheet once, after the third successful operation (never after an error). */
fun maybeAskForReview(ctx: Context) {
    if (!com.muyassir.app.core.Prefs.success()) return
    val act = ctx.activity() ?: return
    runCatching {
        val m = ReviewManagerFactory.create(act)
        m.requestReviewFlow().addOnCompleteListener { t -> if (t.isSuccessful) m.launchReviewFlow(act, t.result) }
    }
}
