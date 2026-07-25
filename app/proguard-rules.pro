# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Google Play services / Maps / Places use reflection for some model classes
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.libraries.places.** { *; }
-keep class com.google.android.gms.common.** { *; }
