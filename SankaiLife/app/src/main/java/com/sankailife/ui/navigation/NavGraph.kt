package com.sankailife.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.sankailife.SankaiApplication
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.sankailife.ui.screens.onboarding.OnboardingScreen
import com.sankailife.ui.screens.customization.CustomizationScreen
import com.sankailife.ui.screens.capsules.CapsulesScreen
import com.sankailife.ui.screens.capsules.CapsulesViewModel
import com.sankailife.ui.screens.customization.CustomizationViewModel
import com.sankailife.ui.screens.profile.AllStatsScreen
import com.sankailife.ui.screens.home.HomeScreen
import com.sankailife.ui.screens.home.HomeViewModel
import com.sankailife.ui.screens.academie.AcademieScreen
import com.sankailife.ui.screens.academie.AcademieViewModel
import com.sankailife.ui.screens.academie.ParcoursScreen
import com.sankailife.ui.screens.academie.ParcoursViewModel
import com.sankailife.ui.screens.life.focus.FocusScreen
import com.sankailife.ui.screens.life.focus.FocusViewModel
import com.sankailife.ui.screens.life.ModeVieScreen
import com.sankailife.ui.screens.life.memo.MemoEditorScreen
import com.sankailife.ui.screens.life.memo.MemoScreen
import com.sankailife.ui.screens.life.memo.MemoViewModel
import com.sankailife.ui.screens.life.flashcards.FlashcardsScreen
import com.sankailife.ui.screens.life.flashcards.FlashcardsViewModel
import com.sankailife.ui.screens.life.objectives.ObjectivesScreen
import com.sankailife.ui.screens.life.objectives.ObjectivesViewModel
import com.sankailife.ui.screens.profile.ProfileScreen
import com.sankailife.ui.screens.profile.ProfileViewModel
import com.sankailife.ui.screens.settings.SettingsScreen
import com.sankailife.ui.screens.settings.SettingsViewModel

@Composable
fun SankaiNavGraph(
    externalRoute: String? = null,
    onExternalRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as SankaiApplication
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Le tutoriel occupe l'écran entier avant tout le reste : le montrer par
    // -dessus la navigation laisserait la barre du bas cliquable pendant qu'on
    // explique ce qu'elle fait. L'etat de chargement est distinct de « pas
    // encore vu » : initialiser a false faisait clignoter le tutoriel chez les
    // utilisateurs existants pendant la lecture de DataStore.
    val tutorielStateFlow = remember(app.preferences) {
        app.preferences.onboardingDone.map { done ->
            if (done) OnboardingGateState.COMPLETE else OnboardingGateState.REQUIRED
        }
    }
    val tutorielState by tutorielStateFlow.collectAsStateWithLifecycle(
        initialValue = OnboardingGateState.LOADING
    )
    when (tutorielState) {
        OnboardingGateState.LOADING -> {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {}
            return
        }
        OnboardingGateState.REQUIRED -> {
            val portee = rememberCoroutineScope()
            OnboardingScreen { dailyMinutes ->
                portee.launch {
                    app.preferences.setDailyMinutes(dailyMinutes)
                    app.preferences.setOnboardingDone(true)
                }
            }
            return
        }
        OnboardingGateState.COMPLETE -> Unit
    }

    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app))

    LaunchedEffect(externalRoute) {
        val route = externalRoute?.takeIf {
            it in setOf(
                Screen.Memo.route,
                Screen.Capsules.route,
                Screen.Academy.route,
                Screen.Focus.route
            )
        } ?: return@LaunchedEffect
        if (currentRoute != route) {
            navController.navigate(route) { launchSingleTop = true }
        }
        onExternalRouteConsumed()
    }

    val showLabels by settingsVm.showNavLabels.collectAsStateWithLifecycle()
    // Ce qui arrive depuis le menu « Partager » d'une autre application.
    //
    // Monté à la racine, pas dans un écran : un partage peut arriver pendant
    // qu'on est sur l'Île ou dans les paramètres, et l'aperçu doit s'afficher
    // là où on se trouve plutôt que d'exiger de naviguer d'abord.
    com.sankailife.ui.screens.life.memo.FeuillePartageEntrant()

    val noBottomBarRoutes = setOf(
        Screen.Settings.route, Screen.MemoEditor.route, Screen.Focus.route,
        Screen.Objectives.route, Screen.Flashcards.route,
        Screen.Customization.route, Screen.AllStats.route,
        Screen.Parcours.route, Screen.Session.route
    )
    val showBottom = currentRoute !in noBottomBarRoutes

    Scaffold(
        // Transparent, et c'est le correctif du « rectangle sous la barre ».
        //
        // Sans cette ligne, le Scaffold peint `colorScheme.background` sur
        // toute sa surface, barre basse comprise, pendant que chaque écran
        // peint son propre fond par-dessus. Les deux ne coïncidant pas, on
        // voyait un bandeau d'une autre couleur derrière la barre flottante.
        // Le fond est désormais peint une seule fois, à la racine.
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottom) {
                SankaiBottomNavBar(
                    currentRoute = currentRoute,
                    showLabels = showLabels,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            // Aucune transition entre ecrans.
            //
            // Le fondu par defaut de Navigation Compose dure 700 ms : passer
            // d'un onglet a l'autre traversait un demi-ecran gris, et
            // l'application paraissait lente alors qu'elle ne l'est pas. Un
            // changement immediat se lit comme une reponse ; un fondu se lit
            // comme une attente.
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None },
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = homeVm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Academy.route) {
                val vm: AcademieViewModel = viewModel(factory = AcademieViewModel.factory(app))
                AcademieScreen(viewModel = vm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Life.route) {
                ModeVieScreen(app = app, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Capsules.route) {
                val vm: CapsulesViewModel = viewModel(factory = CapsulesViewModel.factory(app))
                CapsulesScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Parcours.route) { backEntry ->
                val profileId = backEntry.arguments?.getString("profileId")?.toLongOrNull() ?: -1L
                val vm: ParcoursViewModel = viewModel(factory = ParcoursViewModel.factory(app))
                ParcoursScreen(
                    profileId = profileId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOuvrirUnite = { uniteId ->
                        navController.navigate(Screen.Session.createRoute(profileId, uniteId))
                    }
                )
            }
            composable(Screen.Focus.route) {
                val vm: FocusViewModel = viewModel(factory = FocusViewModel.factory(app))
                FocusScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Memo.route) {
                val vm: MemoViewModel = viewModel(factory = MemoViewModel.factory(app))
                MemoScreen(viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Screen.MemoEditor.createRoute(id)) },
                    onReviser = { id -> navController.navigate(Screen.Flashcards.createRoute(id)) },
                    onReviserErreurs = {
                        navController.navigate(
                            Screen.Flashcards.createRoute(FlashcardsViewModel.PROFIL_ERREURS)
                        )
                    })
            }
            composable(Screen.MemoEditor.route) { backEntry ->
                val profileId = backEntry.arguments?.getString("profileId")?.toLongOrNull() ?: -1L
                val vm: MemoViewModel = viewModel(factory = MemoViewModel.factory(app))
                MemoEditorScreen(profileId = profileId, viewModel = vm, onBack = { navController.popBackStack() })
            }
            // Session guidee : memes ecrans que la revision libre, mais les
            // cartes et la forme de chaque exercice viennent du planificateur.
            composable(Screen.Session.route) { backEntry ->
                val profileId = backEntry.arguments?.getString("profileId")?.toLongOrNull() ?: -1L
                val uniteId = backEntry.arguments?.getString("uniteId").orEmpty()
                val vm: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.factory(app))
                FlashcardsScreen(
                    profileId = profileId,
                    uniteId = uniteId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Flashcards.route) { backEntry ->
                val profileId = backEntry.arguments?.getString("profileId")?.toLongOrNull() ?: -1L
                val vm: FlashcardsViewModel = viewModel(factory = FlashcardsViewModel.factory(app))
                FlashcardsScreen(
                    profileId = profileId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Customization.route) {
                val vm: CustomizationViewModel =
                    viewModel(factory = CustomizationViewModel.factory(app))
                CustomizationScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.AllStats.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app))
                AllStatsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Objectives.route) {
                val vm: ObjectivesViewModel = viewModel(factory = ObjectivesViewModel.factory(app))
                ObjectivesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Profile.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app))
                ProfileScreen(viewModel = vm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsVm,
                    onBack = { navController.popBackStack() },
                    onGererThemes = { navController.navigate(Screen.Customization.route) }
                )
            }
        }
    }
}

private enum class OnboardingGateState {
    LOADING,
    REQUIRED,
    COMPLETE
}
