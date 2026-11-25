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
import dev.buildgate.app.shared.network.SupabaseAppListingRepository
import dev.buildgate.app.shared.network.SupabaseAuthRepository
import dev.buildgate.app.shared.network.SupabaseConfig
import dev.buildgate.app.shared.network.SupabaseEnrollmentRepository
import dev.buildgate.app.shared.network.SupabaseFeedbackRepository
import dev.buildgate.app.shared.network.createBuildGateHttpClient
import dev.buildgate.app.shared.network.createBuildGateSupabaseClientOrNull
import dev.buildgate.app.shared.platform.getPlatformVerifier
import dev.buildgate.app.shared.session.GlobalQaSession
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    // Network, Supabase Client & Active QA Session Coordinator
    single { SupabaseConfig() }
    single { createBuildGateHttpClient(get()) }
    single { createBuildGateSupabaseClientOrNull(get()) }
    single { getPlatformVerifier() }
    single { GlobalQaSession.coordinator }

    // Dual-Mode Repositories (Live Hosted Supabase when local.properties is set; Reactive Demo fallback otherwise)
    single<AuthRepository> {
        val sb = getOrNull<io.github.jan.supabase.SupabaseClient>()
        if (sb != null) SupabaseAuthRepository(sb) else DemoAuthRepository()
    }
    single<AppListingRepository> {
        val sb = getOrNull<io.github.jan.supabase.SupabaseClient>()
        if (sb != null) SupabaseAppListingRepository(sb) else DemoAppListingRepository()
    }
    single<EnrollmentRepository> {
        val sb = getOrNull<io.github.jan.supabase.SupabaseClient>()
        if (sb != null) SupabaseEnrollmentRepository(sb) else DemoEnrollmentRepository()
    }
    single<FeedbackRepository> {
        val sb = getOrNull<io.github.jan.supabase.SupabaseClient>()
        if (sb != null) SupabaseFeedbackRepository(sb) else DemoFeedbackRepository()
    }

    // Lifecycle-managed ViewModels
    viewModelOf(::AuthViewModel)
    viewModelOf(::VerifySessionViewModel)
    viewModelOf(::SubmitFeedbackViewModel)
    viewModelOf(::TriageFeedbackViewModel)
    viewModelOf(::ManageMissionsViewModel)
    viewModelOf(::PublishAppViewModel)
}
