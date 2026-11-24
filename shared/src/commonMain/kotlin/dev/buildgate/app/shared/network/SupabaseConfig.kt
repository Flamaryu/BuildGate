package dev.buildgate.app.shared.network

data class SupabaseConfig(
    val supabaseUrl: String = "https://buildgate.supabase.co",
    val anonKey: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.buildgate_anon_token",
    val authToken: String? = null
)
