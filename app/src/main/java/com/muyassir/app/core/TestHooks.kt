package com.muyassir.app.core

import android.net.Uri

/** Set only by the instrumented UI tests to feed files into a tool instead of the system file picker. Always null in normal use. */
object TestHooks {
    @Volatile var inputs: List<Uri>? = null
}
