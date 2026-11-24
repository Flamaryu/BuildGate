package dev.buildgate.app.app.di

import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.applisting.repository.InMemoryAppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.auth.repository.InMemoryAuthRepository
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.enrollment.repository.InMemoryEnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.entities.feedbackitem.repository.InMemoryFeedbackRepository
import dev.buildgate.app.features.auth.AuthViewModel
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.shared.network.SupabaseConfig
import dev.buildgate.app.shared.network.createBuildGateHttpClient
import dev.buildgate.app.shared.platform.getPlatformVerifier
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {

    // Network & Platform
    single { SupabaseConfig() }
    single { createBuildGateHttpClient(get()) }
    single { getPlatformVerifier() }

    // Repositories
    singleOf(::InMemoryAppListingRepository) bind AppListingRepository::class
    singleOf(::InMemoryEnrollmentRepository) bind EnrollmentRepository::class
    singleOf(::InMemoryFeedbackRepository) bind FeedbackRepository::class
    singleOf(::InMemoryAuthRepository) bind AuthRepository::class

    // ViewModels
    factory { AuthViewModel(get()) }
    factory { VerifySessionViewModel(get(), get(), get()) }
    factory { SubmitFeedbackViewModel(get(), get()) }
    factory { TriageFeedbackViewModel(get()) }
    factory { ManageMissionsViewModel(get()) }
}
