package com.example.splitwsie.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.splitwsie.presentation.analytics.AnalyticsScreen
import com.example.splitwsie.presentation.auth.AuthScreen
import com.example.splitwsie.presentation.welcome.WelcomeScreen
import com.example.splitwsie.presentation.expense.AddExpenseScreen
import com.example.splitwsie.presentation.expense.ExpenseDetailsScreen
import com.example.splitwsie.presentation.group.GroupCreationScreen
import com.example.splitwsie.presentation.group.GroupDetailsScreen
import com.example.splitwsie.presentation.group.GroupSettingsScreen
import com.example.splitwsie.presentation.home.HomeScreen
import com.example.splitwsie.presentation.payment.PaymentScreen
import com.example.splitwsie.presentation.profile.ProfileScreen
import com.example.splitwsie.presentation.settle.PaymentHistoryScreen
import com.example.splitwsie.presentation.settle.SettleUpScreen
import com.example.splitwsie.presentation.friends.FriendsScreen

@Composable
fun SetupNavGraph(
    startDestination: String,
    nextDestination: String = Screen.Auth.route,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(
                isLoggedIn = nextDestination == Screen.Home.route,
                onGetStarted = {
                    navController.navigate(nextDestination) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigateToCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                onNavigateToGroupDetails = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId))
                },
                onNavigateToAddExpense = { groupId ->
                    navController.navigate(Screen.AddExpense.createRoute(groupId = groupId))
                },
                onNavigateToSettleUp = { navController.navigate(Screen.SettleUp.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToFriends = { navController.navigate(Screen.Friends.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) }
            )
        }

        composable(route = Screen.Friends.route) {
            FriendsScreen(
                onNavigateToAddExpense = { friendId ->
                    navController.navigate(Screen.AddExpense.createRoute(friendId = friendId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.Analytics.route) {
            AnalyticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.SettleUp.route) {
            SettleUpScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectDebt = { groupId, toUserId ->
                    navController.navigate(Screen.Payment.createRoute(groupId, toUserId))
                }
            )
        }

        composable(route = Screen.PaymentHistory.route) {
            PaymentHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(route = Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaymentHistory = { navController.navigate(Screen.PaymentHistory.route) },
                onLogoutSuccess = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.CreateGroup.route) {
            GroupCreationScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(route = Screen.GroupDetails.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupDetailsScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddExpense = { gId, expenseId ->
                    if (expenseId != null) {
                        navController.navigate(Screen.ExpenseDetails.createRoute(expenseId = expenseId))
                    } else {
                        navController.navigate(Screen.AddExpense.createRoute(groupId = gId))
                    }
                },
                onNavigateToPayment = { gId, toUserId, expenseId ->
                    navController.navigate(Screen.Payment.createRoute(gId, toUserId, expenseId))
                },
                onNavigateToSettings = { gId ->
                    navController.navigate(Screen.GroupSettings.createRoute(gId))
                },
                onGroupDeleted = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.GroupSettings.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupSettingsScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.ExpenseDetails.route) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId").orEmpty()
            ExpenseDetailsScreen(
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() },
                onEditExpense = { gId, eId -> 
                    navController.navigate(Screen.AddExpense.createRoute(groupId = gId, expenseId = eId))
                }
            )
        }

        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("groupId") { 
                    type = NavType.StringType 
                    nullable = true
                    defaultValue = null
                },
                navArgument("friendId") { 
                    type = NavType.StringType 
                    nullable = true
                    defaultValue = null
                },
                navArgument("expenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            val friendId = backStackEntry.arguments?.getString("friendId")
            val expenseId = backStackEntry.arguments?.getString("expenseId")
            AddExpenseScreen(
                groupId = groupId,
                friendId = friendId,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() },
                onExpenseAdded = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Payment.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("toUserId") { type = NavType.StringType },
                navArgument("expenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val toUserId = backStackEntry.arguments?.getString("toUserId").orEmpty()
            val expenseId = backStackEntry.arguments?.getString("expenseId")

            PaymentScreen(
                groupId = groupId,
                toUserId = toUserId,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() },
                onPaymentSuccess = { navController.popBackStack() }
            )
        }
    }
}
