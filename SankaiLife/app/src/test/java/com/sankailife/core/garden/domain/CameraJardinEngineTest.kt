package com.sankailife.core.garden.domain

import com.sankailife.core.garden.domain.CameraJardinEngine.Cadre
import com.sankailife.core.garden.domain.CameraJardinEngine.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CameraJardinEngineTest {

    private val ZOOM_MIN = 0.6f
    private val ZOOM_MAX = 2.5f

    /** Terrain volontairement plus grand que la vue, sinon tout serait recentré. */
    private fun cadre(echelle: Float) = Cadre(
        largeurVue = 1080f,
        hauteurVue = 1920f,
        minX = 0, maxX = 39,
        minY = 0, maxY = 39,
        pas = 78f * echelle
    )

    // --- Zone morte ---------------------------------------------------------

    @Test
    fun `un tremblement de doigt ne declenche aucun zoom`() {
        // Deux doigts posés ne sont jamais parfaitement immobiles. Sans seuil,
        // chaque micro-variation recalcule la caméra et le terrain vibre.
        assertFalse(CameraJardinEngine.franchitSeuil(1f))
        assertFalse(CameraJardinEngine.franchitSeuil(1.004f))
        assertFalse(CameraJardinEngine.franchitSeuil(0.996f))
        assertTrue(CameraJardinEngine.franchitSeuil(1.05f))
        assertTrue(CameraJardinEngine.franchitSeuil(0.95f))
    }

    @Test
    fun `un facteur aberrant est refuse`() {
        // Un doigt qui apparaît ou disparaît en cours de geste peut produire
        // n'importe quoi ; l'appliquer ferait disparaître le jardin.
        assertFalse(CameraJardinEngine.franchitSeuil(Float.NaN))
        assertFalse(CameraJardinEngine.franchitSeuil(Float.POSITIVE_INFINITY))
        assertFalse(CameraJardinEngine.franchitSeuil(0f))
        assertFalse(CameraJardinEngine.franchitSeuil(-2f))
    }

    // --- Le coeur : le point sous les doigts ne bouge pas --------------------

    @Test
    fun `le point sous les doigts reste sous les doigts`() {
        val avant = 1f
        val camera = Point(-500f, -900f)
        val centroide = Point(400f, 1100f)

        // Point du monde visé, exprimé avant le zoom.
        val mondeX = (centroide.x - camera.x) / (78f * avant)
        val mondeY = (centroide.y - camera.y) / (78f * avant)

        val z = CameraJardinEngine.pincer(
            camera, centroide, avant, facteur = 1.4f,
            zoomMin = ZOOM_MIN, zoomMax = ZOOM_MAX, cadreApres = ::cadre
        )

        // Où ce même point du monde se retrouve-t-il après le zoom ?
        val ecranX = z.camera.x + mondeX * 78f * z.echelle
        val ecranY = z.camera.y + mondeY * 78f * z.echelle

        assertEquals(centroide.x, ecranX, 0.5f)
        assertEquals(centroide.y, ecranY, 0.5f)
    }

    @Test
    fun `zoomer loin du centre ne ramene pas la vue au centre`() {
        // Le défaut d'origine : le zoom partait toujours vers le coin
        // supérieur gauche, quel que soit l'endroit pincé.
        val camera = Point(-800f, -1400f)
        val coin = Point(950f, 1200f)

        val mondeX = (coin.x - camera.x) / 78f
        val mondeY = (coin.y - camera.y) / 78f

        val z = CameraJardinEngine.pincer(
            camera, coin, 1f, 1.6f, ZOOM_MIN, ZOOM_MAX, ::cadre
        )

        assertEquals(coin.x, z.camera.x + mondeX * 78f * z.echelle, 0.5f)
        assertEquals(coin.y, z.camera.y + mondeY * 78f * z.echelle, 0.5f)
    }

    @Test
    fun `au bord du terrain la limite l'emporte sur le point sous les doigts`() {
        // Ce n'est pas une entorse à la règle, c'est sa hiérarchie : garder le
        // point exactement sous les doigts près d'un bord obligerait à laisser
        // le terrain sortir de l'écran. Le zoom se poursuit, la vue reste
        // simplement collée au bord.
        val camera = Point(-800f, -1400f)
        val basDeLEcran = Point(950f, 1750f)

        val z = CameraJardinEngine.pincer(
            camera, basDeLEcran, 1f, 1.6f, ZOOM_MIN, ZOOM_MAX, ::cadre
        )
        val c = cadre(z.echelle)

        // L'échelle a bien changé…
        assertEquals(1.6f, z.echelle, 0.0001f)
        // …et la vue est exactement sur sa limite basse, pas au-delà.
        assertEquals(c.hauteurVue - (c.maxY + 1) * c.pas, z.camera.y, 0.5f)
        assertEquals(CameraJardinEngine.borner(z.camera, c).y, z.camera.y, 0.001f)
    }

    @Test
    fun `a la borne du zoom la vue ne glisse pas`() {
        // L'échelle est déjà au maximum : le facteur demandé est écrêté. Si on
        // appliquait le facteur demandé plutôt que le rapport réel, le terrain
        // fuirait alors que rien ne grandit plus.
        val camera = Point(-500f, -900f)
        val centroide = Point(540f, 960f)

        val z = CameraJardinEngine.pincer(
            camera, centroide, ZOOM_MAX, facteur = 2f, ZOOM_MIN, ZOOM_MAX, ::cadre
        )

        assertEquals(ZOOM_MAX, z.echelle, 0.0001f)
        assertEquals(camera.x, z.camera.x, 0.5f)
        assertEquals(camera.y, z.camera.y, 0.5f)
    }

    @Test
    fun `l'echelle reste dans ses bornes`() {
        val c = Point(-100f, -100f)
        val centre = Point(540f, 960f)
        assertEquals(
            ZOOM_MAX,
            CameraJardinEngine.pincer(c, centre, 2f, 10f, ZOOM_MIN, ZOOM_MAX, ::cadre).echelle,
            0.0001f
        )
        assertEquals(
            ZOOM_MIN,
            CameraJardinEngine.pincer(c, centre, 1f, 0.01f, ZOOM_MIN, ZOOM_MAX, ::cadre).echelle,
            0.0001f
        )
    }

    @Test
    fun `le zoom rend deja une camera bornee`() {
        // C'était la cause du saut en fin de geste : la caméra partait libre
        // pendant le pincement, et un effet différé la ramenait d'un coup.
        val z = CameraJardinEngine.pincer(
            Point(9_000f, 9_000f), Point(540f, 960f), 1f, 1.2f, ZOOM_MIN, ZOOM_MAX, ::cadre
        )
        val borne = CameraJardinEngine.borner(z.camera, cadre(z.echelle))
        assertEquals(borne.x, z.camera.x, 0.001f)
        assertEquals(borne.y, z.camera.y, 0.001f)
    }

    @Test
    fun `vingt pincements enchaines ne derivent pas`() {
        // Le tremblement se voit surtout à l'usage répété : une petite erreur
        // réintroduite à chaque geste finit par déplacer le jardin.
        var camera = Point(-500f, -900f)
        var echelle = 1f
        val centroide = Point(600f, 1000f)

        val mondeDepart = Point(
            (centroide.x - camera.x) / (78f * echelle),
            (centroide.y - camera.y) / (78f * echelle)
        )

        repeat(20) {
            val z = CameraJardinEngine.pincer(
                camera, centroide, echelle,
                facteur = if (it % 2 == 0) 1.1f else 1f / 1.1f,
                ZOOM_MIN, ZOOM_MAX, ::cadre
            )
            camera = z.camera
            echelle = z.echelle
        }

        val ecranX = camera.x + mondeDepart.x * 78f * echelle
        val ecranY = camera.y + mondeDepart.y * 78f * echelle
        assertTrue(
            "Derive horizontale de ${abs(ecranX - centroide.x)} px",
            abs(ecranX - centroide.x) < 2f
        )
        assertTrue(
            "Derive verticale de ${abs(ecranY - centroide.y)} px",
            abs(ecranY - centroide.y) < 2f
        )
    }

    // --- Limites ------------------------------------------------------------

    @Test
    fun `un terrain plus petit que l'ecran reste centre`() {
        // Sinon un jardin de début de partie peut être poussé dans un coin,
        // entouré de vide.
        val petit = Cadre(1080f, 1920f, minX = 18, maxX = 21, minY = 18, maxY = 21, pas = 78f)
        val a = CameraJardinEngine.borner(Point(-5_000f, -5_000f), petit)
        val b = CameraJardinEngine.borner(Point(5_000f, 5_000f), petit)
        assertEquals(a.x, b.x, 0.001f)
        assertEquals(a.y, b.y, 0.001f)
        assertEquals(CameraJardinEngine.centree(petit).x, a.x, 0.001f)
    }

    @Test
    fun `on ne peut pas pousser le terrain hors de l'ecran`() {
        val c = cadre(1f)
        val loin = CameraJardinEngine.borner(Point(50_000f, 50_000f), c)
        assertTrue(loin.x <= 0.001f)
        assertTrue(loin.y <= 0.001f)

        val tresLoin = CameraJardinEngine.borner(Point(-50_000f, -50_000f), c)
        assertTrue(tresLoin.x >= c.largeurVue - (c.maxX + 1) * c.pas - 0.001f)
        assertTrue(tresLoin.y >= c.hauteurVue - (c.maxY + 1) * c.pas - 0.001f)
    }

    // --- Stabilisation ------------------------------------------------------

    @Test
    fun `aucun deplacement pendant le pincement`() {
        assertFalse(CameraJardinEngine.peutDeplacer(true, 10_000L, 9_000L))
    }

    @Test
    fun `aucun deplacement juste apres le pincement`() {
        // Les doigts ne se lèvent jamais ensemble : sans ce délai, le dernier
        // doigt encore posé fait partir la vue de côté.
        val fin = 10_000L
        assertFalse(CameraJardinEngine.peutDeplacer(false, fin + 10L, fin))
        assertFalse(
            CameraJardinEngine.peutDeplacer(
                false, fin + CameraJardinEngine.STABILISATION_MS - 1L, fin
            )
        )
        assertTrue(
            CameraJardinEngine.peutDeplacer(
                false, fin + CameraJardinEngine.STABILISATION_MS, fin
            )
        )
    }

    @Test
    fun `le deplacement est libre quand aucun zoom n'a eu lieu`() {
        assertTrue(CameraJardinEngine.peutDeplacer(false, 5_000L, 0L))
    }
}
