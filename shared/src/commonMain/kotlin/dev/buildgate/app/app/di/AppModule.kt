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
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.shared.network.SupabaseConfig
import dev.buildgate.app.shared.network.createBuildGateHttpClient
import dev.buildgate.app.shared.platform.getPlatformVerifier
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {

    // Network & Platform
    single { SupabaseConfig() }
    single { createBuildGateHttpClient(get()) }
    single { getPlatformVerifier() }

    // Reactive Repositories (Demo fallback until Hosted Supabase keys are provided)
    singleOf(::DemoAppListingRepository) bind AppListingRepository::class
    singleOf(::DemoEnrollmentRepository) bind EnrollmentRepository::class
    singleOf(::DemoFeedbackRepository) bind FeedbackRepository::class
    singleOf(::DemoAuthRepository) bind AuthRepository::class

    // Lifecycle-managed ViewModels
    viewModelOf(::AuthViewModel)
    viewModelOf(::VerifySessionViewModel)
    viewModelOf(::SubmitFeedbackViewModel)
    viewModelOf(::TriageFeedbackViewModel)
    viewModelOf(::ManageMissionsViewModel)
}
