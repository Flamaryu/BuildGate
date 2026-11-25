package dev.buildgate.app.app.di

import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.applisting.repository.DemoAppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.auth.repository.DemoAuthRepository
import dev.buildgate.app.entities.enrollment.repository.DemoEnrollmentRepository
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.repository.DemoFeedbackRepository
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.features.auth.AuthViewModel
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.publishapp.PublishAppViewModel
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.shared.network.BuildGateSupabaseClientHolder
import dev.buildgate.app.shared.network.SupabaseAppListingRepository
import dev.buildgate.app.shared.network.SupabaseAuthRepository
import dev.buildgate.app.shared.network.SupabaseConfig
import dev.buildgate.app.shared.network.SupabaseEnrollmentRepository
import dev.buildgate.app.shared.network.SupabaseFeedbackRepository
import dev.buildgate.app.shared.network.createBuildGateHttpClient
import dev.buildgate.app.shared.network.createBuildGateSupabaseClientOrNull
import dev.buildgate.app.shared.platform.getPlatformVerifier
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.buildgate.app.shared.session.GlobalQaSession
import org.koin.compose.currentKoinScope
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val appModule = module {

    // Network, Supabase Client & Active QA Session Coordinator
    single { SupabaseConfig() }
    single { createBuildGateHttpClient(get()) }
    single { BuildGateSupabaseClientHolder(createBuildGateSupabaseClientOrNull(get())) }
    single { getPlatformVerifier() }
    single { GlobalQaSession.coordinator }

    // Dual-Mode Repositories (Live Hosted Supabase when local.properties is set; Reactive Demo fallback otherwise)
    single<AuthRepository> {
        val sb = get<BuildGateSupabaseClientHolder>().client
        if (sb != null) SupabaseAuthRepository(sb) else DemoAuthRepository()
    }
    single<AppListingRepository> {
        val sb = get<BuildGateSupabaseClientHolder>().client
        if (sb != null) SupabaseAppListingRepository(sb) else DemoAppListingRepository()
    }
    single<EnrollmentRepository> {
        val sb = get<BuildGateSupabaseClientHolder>().client
        if (sb != null) SupabaseEnrollmentRepository(sb) else DemoEnrollmentRepository()
    }
    single<FeedbackRepository> {
        val sb = get<BuildGateSupabaseClientHolder>().client
        if (sb != null) SupabaseFeedbackRepository(sb) else DemoFeedbackRepository()
    }

    // Lifecycle-managed ViewModels (instantiated via Compose's viewModel() + Koin scope)
    factoryOf(::AuthViewModel)
    factoryOf(::VerifySessionViewModel)
    factoryOf(::SubmitFeedbackViewModel)
    factoryOf(::TriageFeedbackViewModel)
    factoryOf(::ManageMissionsViewModel)
    factoryOf(::PublishAppViewModel)
}

/**
 * Resolves a [ViewModel] from the active Koin scope and retains it inside Compose's
 * [androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner].
 *
 * Compiled directly against the project's lifecycle-viewmodel-compose version to avoid
 * Koin/Compose WasmJS IR linkage mismatches.
 */
@Composable
inline fun <reified T : ViewModel> koinScopedViewModel(): T {
    val scope = currentKoinScope()
    return viewModel<T> { scope.get<T>() }
}
