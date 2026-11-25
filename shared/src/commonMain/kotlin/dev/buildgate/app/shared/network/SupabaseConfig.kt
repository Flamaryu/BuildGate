package dev.buildgate.app.shared.network

data class SupabaseConfig(
    val supabaseUrl: String = GeneratedSupabaseEnv.SUPABASE_URL,
    val publishableKey: String = GeneratedSupabaseEnv.SUPABASE_PUBLISHABLE_KEY
) {
    val isLiveConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("YOUR_PROJECT_REF") &&
            publishableKey.isNotBlank() &&
            !publishableKey.contains("YOUR_KEY_HERE")
}
