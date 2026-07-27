# ---------------------------------------------------------------------------
# Règles R8 pour Sankai Life.
#
# Les bibliothèques modernes (Room, AdMob, Compose, WorkManager, DataStore)
# embarquent leurs propres règles. Ce fichier ne couvre que ce qui est propre
# au projet ou notoirement fragile.
# ---------------------------------------------------------------------------

# Conserver les numéros de ligne : sans eux, un rapport de plantage devient
# illisible et le débogage d'un crash utilisateur impossible.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Entités et modèles ----------------------------------------------------
# Room génère du code qui référence les entités par nom.
-keep class com.sankailife.core.data.db.entities.** { *; }
-keep class com.sankailife.core.domain.model.** { *; }

# --- Receivers déclarés dans le manifeste ----------------------------------
# Android les instancie par réflexion à partir de leur nom : les renommer
# ferait échouer silencieusement toutes les notifications.
-keep class com.sankailife.core.notifications.MemoAlarmReceiver { *; }
-keep class com.sankailife.core.notifications.SystemEventsReceiver { *; }

# --- WorkManager -----------------------------------------------------------
# Même raison : les Workers sont instanciés par réflexion.
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Google Mobile Ads -----------------------------------------------------
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# --- Kotlin coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**

# --- Silencer les avertissements de classes optionnelles absentes ----------
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
