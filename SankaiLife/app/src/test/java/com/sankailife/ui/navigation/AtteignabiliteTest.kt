package com.sankailife.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Chaque écran déclaré doit être atteignable.
 *
 * **Ce test existe parce que le défaut est arrivé deux fois en une semaine.**
 *
 * L'Île a remplacé le Jardin, et l'embauche des Mimos — qui ne vivait que sur
 * l'écran du Jardin — est devenue inaccessible. Le système entier tournait
 * encore, plafond de l'Atelier compris, mais plus personne ne pouvait recruter
 * le premier employé. Rien ne l'a signalé : ni le compilateur, ni les tests, ni
 * le lint. Un écran orphelin compile parfaitement.
 *
 * Puis l'Académie a remplacé le Mode Vie, et Focus et Objectifs ont failli
 * subir exactement le même sort, dans la même session.
 *
 * Le test lit les sources plutôt que d'exercer du code, ce qui est inhabituel.
 * C'est assumé : la question posée — « existe-t-il un endroit d'où l'on peut
 * aller là ? » — porte sur la structure du projet, pas sur le comportement
 * d'une fonction, et aucun test de comportement ne l'aurait attrapée.
 */
class AtteignabiliteTest {

    /**
     * Écrans volontairement sans point d'entrée, avec la raison.
     *
     * Y ajouter une ligne est une décision, et c'est le but : on ne rend pas un
     * écran inaccessible par distraction, on l'assume par écrit.
     */
    private val orphelinsAssumes = mapOf(
        "Garden" to
            "Remplacé par l'Île. L'écran survit en attendant la suppression du " +
                "Jardin, qui ne peut pas se faire tant que l'Île dépend de ses moteurs."
    )

    private fun racineSources(): File {
        var dossier = File("").absoluteFile
        repeat(6) {
            val candidat = File(dossier, "app/src/main/java/com/sankailife")
            if (candidat.isDirectory) return candidat
            val direct = File(dossier, "src/main/java/com/sankailife")
            if (direct.isDirectory) return direct
            dossier = dossier.parentFile ?: return@repeat
        }
        error("Sources introuvables depuis ${File("").absolutePath}")
    }

    @Test
    fun `chaque ecran a un point d'entree ou une raison de ne pas en avoir`() {
        val racine = racineSources()
        val declarations = File(racine, "ui/navigation/Screen.kt").readText()

        val ecrans = Regex("""object\s+(\w+)\s*:\s*Screen\(""")
            .findAll(declarations).map { it.groupValues[1] }.toList()
        assertTrue("Aucun écran trouvé : le test ne vérifie plus rien", ecrans.size > 5)

        // Un point d'entrée est un **appel de navigation**, pas une mention.
        //
        // Écarter tout NavGraph.kt serait plus simple et faux : ce fichier
        // déclare les destinations — `composable(Screen.X.route)` — mais il en
        // câble aussi de vraies, par les rappels qu'il passe aux écrans. Mon
        // premier jet excluait le fichier entier et signalait l'éditeur de
        // mémo comme orphelin alors qu'on y accède tous les jours.
        //
        // Sont donc comptés `navigate(...)`, `onNavigate(...)` — que le même
        // motif attrape — et `NavItem(...)` pour la barre du bas. Sont ignorées
        // les déclarations et les comparaisons de route, qui ne mènent nulle
        // part.
        val sources = racine.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.readText() }
            .toList()

        val sansEntree = ecrans.filter { nom ->
            val motif = Regex("""(?:[nN]avigate|NavItem)\s*\(\s*Screen\.$nom\.(?:route|createRoute)""")
            sources.none { motif.containsMatchIn(it) }
        }

        val inattendus = sansEntree.filter { it !in orphelinsAssumes }
        assertTrue(
            "Écran(s) devenu(s) inaccessible(s) : $inattendus. " +
                "Soit un point d'entrée manque, soit il faut l'assumer dans " +
                "orphelinsAssumes en disant pourquoi.",
            inattendus.isEmpty()
        )

        // L'inverse compte aussi : un orphelin qui redevient accessible doit
        // sortir de la liste, sinon elle se transforme en folklore.
        val plusOrphelins = orphelinsAssumes.keys.filter { it !in sansEntree }
        assertTrue(
            "Ces écrans sont de nouveau accessibles et n'ont plus à figurer " +
                "dans orphelinsAssumes : $plusOrphelins",
            plusOrphelins.isEmpty()
        )
    }
}
