-keep class androidx.glance.** { *; }
-keep class androidx.datastore.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Shizuku creates this Binder implementation by reflection in a separate process.
-keep class com.gree1d.reappzuku.core.shell.ShizukuUserServiceImpl {
    public <init>();
}
