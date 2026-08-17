package io.cherry.player

import android.app.Application

/**
 * Application class. Kept intentionally empty for v1 — DataStore and ExoPlayer
 * instances are scoped to the ViewModel layer, not the application process.
 */
class CherryApp : Application()