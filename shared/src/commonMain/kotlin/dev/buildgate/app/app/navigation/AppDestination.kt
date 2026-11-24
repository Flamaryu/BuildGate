package dev.buildgate.app.app.navigation

enum class AppDestination(
    val title: String,
    val icon: String
) {
    DISCOVERY("Discovery", "🔍"),
    SESSION_VERIFICATION("Verification", "✅"),
    TRIAGE_BOARD("Triage & Bugs", "📊")
}
