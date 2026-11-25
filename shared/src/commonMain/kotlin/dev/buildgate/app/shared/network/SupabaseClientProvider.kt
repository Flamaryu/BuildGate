package dev.buildgate.app.shared.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

fun createBuildGateSupabaseClientOrNull(config: SupabaseConfig): SupabaseClient? {
    if (!config.isLiveConfigured) return null
    return createSupabaseClient(
        supabaseUrl = config.supabaseUrl,
        supabaseKey = config.publishableKey
    ) {
        install(Auth) {
            scheme = "buildgate"
            host = "auth"
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}
