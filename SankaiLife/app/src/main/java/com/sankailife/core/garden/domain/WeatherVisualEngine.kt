package com.sankailife.core.garden.domain

import kotlin.math.abs

/** Niveau de détail des effets animés du Jardin. */
enum class GraphicsQuality(val id: String, val libelle: String) {
    LOW("low", "Faible"),
    NORMAL("normal", "Normale"),
    HIGH("high", "Élevée");

    companion object {
        fun parId(id: String): GraphicsQuality =
            entries.firstOrNull { it.id == id.lowercase() } ?: NORMAL
    }
}

/** Vent unique partagé par les nuages, la pluie et les futurs effets végétaux. */
data class GardenWindState(
    /** 0° = est, 90° = sud. */
    val directionDegrees: Float,
    /** Intensité normalisée, de 0 à 1. */
    val strength: Float
)

/** Paramètres de la couche d'ombres, sans dépendance envers Compose ou Android. */
data class CloudShadowState(
    val enabled: Boolean,
    val density: Float,
    val opacity: Float,
    val speed: Float,
    val directionDegrees: Float,
    val scale: Float,
    val layers: Int,
    val tintArgb: Long
)

/** Filtre couvert séparé des ombres mobiles. */
data class WeatherLightingState(
    val tintArgb: Long,
    val opacity: Float,
    val saturation: Float
)

data class GardenWeatherVisualState(
    val wind: GardenWindState,
    val clouds: CloudShadowState,
    val lighting: WeatherLightingState
)

/**
 * Traduit la météo mécanique en cibles purement visuelles.
 *
 * Rien ici ne modifie la croissance : [MoistureEngine] reste l'unique source
 * des effets agricoles. Le rendu peut donc être désactivé sans changer le jeu.
 */
object WeatherVisualEngine {

    fun state(
        weather: WeatherEngine.Meteo,
        phase: DayNightEngine.Phase,
        quality: GraphicsQuality,
        dayId: String,
        reduceMotion: Boolean = false
    ): GardenWeatherVisualState {
        val wind = wind(dayId, weather)
        val layers = when (quality) {
            GraphicsQuality.LOW -> 1
            GraphicsQuality.NORMAL -> 2
            GraphicsQuality.HIGH -> 3
        }

        val (density, baseOpacity, scale) = when (weather) {
            WeatherEngine.Meteo.NUAGEUX -> Triple(0.58f, 0.14f, 1.25f)
            WeatherEngine.Meteo.PLUIE -> Triple(0.78f, 0.16f, 1.12f)
            WeatherEngine.Meteo.ORAGE -> Triple(0.92f, 0.13f, 1.04f)
            else -> Triple(0f, 0f, 1.25f)
        }
        val phaseMultiplier = when (phase) {
            DayNightEngine.Phase.JOUR -> 1f
            DayNightEngine.Phase.AUBE -> 0.78f
            DayNightEngine.Phase.CREPUSCULE -> 0.62f
            DayNightEngine.Phase.NUIT -> 0.22f
        }
        val tint = when (phase) {
            DayNightEngine.Phase.JOUR, DayNightEngine.Phase.AUBE -> 0xFF536878
            DayNightEngine.Phase.CREPUSCULE -> 0xFF5A485E
            DayNightEngine.Phase.NUIT -> 0xFF26344F
        }
        val cloudy = density > 0f
        val speed = if (reduceMotion || !cloudy) 0f else 2f + wind.strength * 6f

        val lighting = when (weather) {
            WeatherEngine.Meteo.CANICULE -> WeatherLightingState(0xFFFFB36A, 0.035f, 0.98f)
            WeatherEngine.Meteo.SOLEIL -> WeatherLightingState(0xFFFFFFFF, 0f, 1f)
            WeatherEngine.Meteo.NUAGEUX -> WeatherLightingState(0xFF718595, 0.075f, 0.93f)
            WeatherEngine.Meteo.PLUIE -> WeatherLightingState(0xFF587184, 0.105f, 0.88f)
            WeatherEngine.Meteo.ORAGE -> WeatherLightingState(0xFF3F536B, 0.14f, 0.82f)
        }

        return GardenWeatherVisualState(
            wind = wind,
            clouds = CloudShadowState(
                enabled = cloudy,
                density = density,
                opacity = baseOpacity * phaseMultiplier,
                speed = speed,
                directionDegrees = wind.directionDegrees,
                scale = scale,
                layers = layers,
                tintArgb = tint
            ),
            lighting = lighting
        )
    }

    /** Vent stable pour une journée, mais plus marqué sous l'orage. */
    fun wind(dayId: String, weather: WeatherEngine.Meteo): GardenWindState {
        val hash = stablePositiveHash("wind:$dayId")
        val direction = (hash % 360).toFloat()
        val variation = ((hash / 360) % 41) / 100f
        val base = when (weather) {
            WeatherEngine.Meteo.CANICULE -> 0.18f
            WeatherEngine.Meteo.SOLEIL -> 0.22f
            WeatherEngine.Meteo.NUAGEUX -> 0.35f
            WeatherEngine.Meteo.PLUIE -> 0.52f
            WeatherEngine.Meteo.ORAGE -> 0.72f
        }
        return GardenWindState(direction, (base + variation).coerceIn(0f, 1f))
    }

    private fun stablePositiveHash(value: String): Long = abs(value.hashCode().toLong())
}
