# R8 / ProGuard rules for SETTLE.
#
# Release builds now shrink and obfuscate. These rules keep the pieces that are reached
# reflectively (Room, Hilt, Compose) rather than through a call site R8 can see.

# --- Room ---------------------------------------------------------------------------------
# Entities and DAOs are resolved by generated code and by name at runtime.
-keep class com.example.splitwsie.data.local.entity.** { *; }
-keep class com.example.splitwsie.data.local.Converters { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- Domain models ------------------------------------------------------------------------
# Enum names are persisted as strings (SplitType, SettlementStatus, PaymentMethod,
# ExpenseCategory). Obfuscating them would break every row already written to the database.
-keepclassmembers enum com.example.splitwsie.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** entries();
}
-keep class com.example.splitwsie.domain.model.** { *; }

# --- Hilt / Dagger ------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# --- Kotlin -------------------------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlinx.coroutines.**

# --- Crash readability --------------------------------------------------------------------
# Keep line numbers so release crash reports remain diagnosable, but hide the original
# source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
