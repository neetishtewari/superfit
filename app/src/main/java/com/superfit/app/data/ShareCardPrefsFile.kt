package com.superfit.app.data

import android.content.Context

/** Android keeps the share screen's choices in their own preferences file, for every account on the phone. */
fun ShareCardPrefs.Companion.of(context: Context): ShareCardPrefs =
    ShareCardPrefs(AndroidPrefs(context.applicationContext.getSharedPreferences(ShareCardPrefs.FILE, Context.MODE_PRIVATE)))
