package com.sankailife.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.sankailife.SankaiApplication
import com.sankailife.ui.screens.arenas.ArenasScreen
import com.sankailife.ui.screens.arenas.ArenasViewModel
import com.sankailife.ui.screens.challenges.ChallengesScreen
import com.sankailife.ui.screens.customization.CustomizationScreen
import com.sankailife.ui.screens.garden.GardenScreen
import com.sankailife.ui.screens.garden.GardenViewModel
import com.sankailife.ui.screens.customization.CustomizationViewModel
import com.sankailife.ui.screens.profile.AllStatsScreen
import com.sankailife.ui.screens.challenges.ChallengesViewModel
import com.sankailife.ui.screens.home.HomeScreen
import com.sankailife.ui.screens.home.HomeViewModel
import com.sankailife.ui.screens.life.LifeScreen
import com.sankailife.ui.screens.life.LifeViewModel
import com.sankailife.ui.screens.life.focus.FocusScreen
import com.sankailife.ui.screens.life.focus.FocusViewModel
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
import com.sankailife.ui.screens.shop.ShopScreen
import com.sankailife.ui.screens.shop.ShopViewModel

@Composable
fun SankaiNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as SankaiApplication
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val homeVm: HomeViewModel        = viewModel(factory = HomeViewModel.factory(app))
    val challengesVm: ChallengesViewModel = viewModel(factory = ChallengesViewModel.factory(app))
    val settingsVm: SettingsViewModel= viewModel(factory = SettingsViewModel.factory(app))

    val showLabels by settingsVm.showNavLabels.collectAsState()
    val claimableCount by challengesVm.claimableCount.collectAsState()
    val coffresPrets by homeVm.coffresPrets.collectAsState()
    val user by homeVm.user.collectAsState()

    // Le verrou qu'on vient de toucher, s'il y en a un.
    var verrouAffiche by remember {
        mutableStateOf<com.sankailife.core.domain.engine.DeblocageEngine.Verrou?>(null)
    }

    verrouAffiche?.let { v ->
        FeuilleVerrou(verrou = v, onFermer = { verrouAffiche = null })
    }

    val noBottomBarRoutes = setOf(
        Screen.Settings.route, Screen.MemoEditor.route,
        Screen.Objectives.route, Screen.Flashcards.route, Screen.Arenas.route,
        Screen.Customization.route, Screen.AllStats.route,
        // Le jardin masque la navigation de l'app : c'est un mode isolé, pas
        // un onglet de plus.
        Screen.Garden.route
    )
    val showBottom = currentRoute !in noBottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottom) {
                SankaiBottomNavBar(
                    currentRoute = currentRoute,
                    showLabels = showLabels,
                    challengeBadge = claimableCount,
                    homeBadge = coffresPrets,
                    niveau = user.level,
                    onVerrou = { verrouAffiche = it },
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
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = homeVm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Life.route) {
                val vm: LifeViewModel = viewModel(factory = LifeViewModel.factory(app))
                LifeScreen(viewModel = vm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Focus.route) {
                val vm: FocusViewModel = viewModel(factory = FocusViewModel.factory(app))
                FocusScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Memo.route) {
                val vm: MemoViewModel = viewModel(factory = MemoViewModel.factory(app))
                MemoScreen(viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Screen.MemoEditor.createRoute(id)) })
            }
            composable(Screen.MemoEditor.route) { backEntry ->
                val profileId = backEntry.arguments?.getString("profileId")?.toLongOrNull() ?: -1L
                val vm: MemoViewModel = viewModel(factory = MemoViewModel.factory(app))
                MemoEditorScreen(profileId = profileId, viewModel = vm, onBack = { navController.popBackStack() })
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
            composable(Screen.Garden.route) {
                val vm: GardenViewModel = viewModel(factory = GardenViewModel.factory(app))
                GardenScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Arenas.route) {
                val vm: ArenasViewModel = viewModel(factory = ArenasViewModel.factory(app))
                ArenasScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Objectives.route) {
                val vm: ObjectivesViewModel = viewModel(factory = ObjectivesViewModel.factory(app))
                ObjectivesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Challenges.route) {
                ChallengesScreen(viewModel = challengesVm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Shop.route) {
                val vm: ShopViewModel = viewModel(factory = ShopViewModel.factory(app))
                ShopScreen(viewModel = vm)
            }
            composable(Screen.Profile.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(app))
                ProfileScreen(viewModel = vm, onNavigate = { navController.navigate(it) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsVm, onBack = { navController.popBackStack() })
            }
        }
    }
}
