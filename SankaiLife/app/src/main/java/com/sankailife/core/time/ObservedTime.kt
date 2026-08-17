package com.sankailife.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Instant civil observé par les écrans dont les données vieillissent toutes seules.
 *
 * Le flux est froid : aucune boucle ne tourne tant qu'un écran ne le collecte. Il
 * s'aligne ensuite sur la prochaine frontière de minute, ce qui évite une dérive
 * progressive après plusieurs suspensions du processus.
 */
data class ObservedMinute(
    val epochMillis: Long,
    val localDate: LocalDate
)

fun observedMinutes(): Flow<ObservedMinute> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(
            ObservedMinute(
                epochMillis = now,
                // La zone est relue à chaque émission : un changement de fuseau
                // ne nécessite donc pas de recréer le ViewModel.
                localDate = Instant.ofEpochMilli(now)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            )
        )

        val untilNextMinute = MILLIS_PER_MINUTE - Math.floorMod(now, MILLIS_PER_MINUTE)
        delay(untilNextMinute.coerceAtLeast(1L))
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
