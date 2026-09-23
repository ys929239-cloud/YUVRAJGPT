package com.example.util

import com.example.BuildConfig

/**
 * Production-ready logger.
 * In debug builds, logs are forwarded to android.util.Log.
 * In release builds, all log statements are completely disabled and stripped.
 */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                android.util.Log.w(tag, message, throwable)
            } else {
                android.util.Log.w(tag, message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        }
    }
}
