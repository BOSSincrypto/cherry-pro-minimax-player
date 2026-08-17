# ProGuard / R8 rules for Cherry Player.
#
# Strategy: aggressively shrink + obfuscate. Keep only what R8 cannot prove
# safe to remove — primarily Media3's reflection-driven wiring and Compose
# runtime metadata.

# ---- Kotlin / coroutines ----
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation
-dontwarn kotlinx.coroutines.**

# ---- Compose runtime ----
# Compose compiler emits the rules it needs via AGP; nothing to add.

# ---- Media3 ----
# Media3 ships its own consumer-rules.pro under each artifact, so most rules
# are inherited. Add safety nets for the bits R8 still strips in some setups.
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# ---- DataStore ----
-keep class androidx.datastore.preferences.protobuf.** { *; }
-dontwarn androidx.datastore.**

# ---- Strip everything else ----
-allowaccessmodification
-repackageclasses 'ccp'
-overloadaggressively

# Drop debug-only Log calls in release builds. ProGuard does not strip them
# automatically for kotlinx and java.util.logging without these rules.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# Scoped to info+ to avoid stripping loggers' own equals/hashCode/notify.
-assumenosideeffects class java.util.logging.Logger {
    public static * info(...);
    public static * fine(...);
    public static * finer(...);
    public static * finest(...);
    public static * config(...);
}

# Keep app entry points so the OS can still instantiate us.
-keep class io.cherry.player.MainActivity { *; }
-keep class io.cherry.player.CherryApp { *; }
-keep class io.cherry.player.player.PlaybackService { *; }