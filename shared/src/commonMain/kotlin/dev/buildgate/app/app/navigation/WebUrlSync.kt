package dev.buildgate.app.app.navigation

expect fun getInitialBrowserRouteHash(): String?

expect fun syncBrowserRouteHash(hash: String)
