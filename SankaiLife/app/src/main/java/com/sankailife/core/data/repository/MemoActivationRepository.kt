package com.sankailife.core.data.repository

import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase

/** Applique réellement la limite de profils mémo actifs achetée par le joueur. */
class MemoActivationRepository(private val db: SankaiDatabase) {

    sealed interface Resultat {
        data object MisAJour : Resultat
        data class LimiteAtteinte(val slots: Int) : Resultat
        data object ProfilIntrouvable : Resultat
    }

    suspend fun definirActif(profileId: Long, actif: Boolean): Resultat =
        db.withTransaction {
            val dao = db.memoDao()
            val profil = dao.getProfile(profileId)
                ?: return@withTransaction Resultat.ProfilIntrouvable

            if (profil.isActive == actif) return@withTransaction Resultat.MisAJour

            if (actif) {
                val slots = db.userDao().getUserOnce()?.moduleSlots?.coerceAtLeast(1) ?: 1
                val actifs = dao.getActiveProfilesOnce().count { it.id != profileId }
                if (actifs >= slots) {
                    return@withTransaction Resultat.LimiteAtteinte(slots)
                }
            }

            dao.setActive(profileId, actif)
            Resultat.MisAJour
        }
}
