# R8 shrinks, optimises and obfuscates the release builds (see app/build.gradle.kts).
# Hilt, Room, WorkManager, Compose, DataStore and the manifest components ship or generate their own rules.

# The names of these enums are stored in the database and in backup files and read back with valueOf():
# they must keep their names and must not be merged or unboxed.
-keep enum com.boratreino.core.model.** { *; }
-keepclassmembers enum com.boratreino.feature.** { *; }

# Health Connect's client library reads its protocol buffer classes through reflection.
-keep class androidx.health.platform.client.proto.** { *; }
-dontwarn androidx.health.platform.client.proto.**
