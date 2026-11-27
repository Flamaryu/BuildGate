package dev.buildgate.app.shared.network

import dev.buildgate.app.entities.applisting.model.AppBoostType
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.auth.model.AuthUser
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.auth.repository.DisplayNameAvailability
import dev.buildgate.app.entities.dailyverification.model.DailyVerification
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.domain.VerificationOutcome
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.entities.feedbackitem.repository.StatusUpdateResult
import dev.buildgate.app.entities.testerprofile.model.KarmaEntry
import dev.buildgate.app.entities.testerprofile.model.KarmaReason
import dev.buildgate.app.entities.testerprofile.model.TesterProfile
import dev.buildgate.app.entities.testmission.model.TestMission
import dev.buildgate.app.shared.platform.DeviceMetadata
import dev.buildgate.app.shared.time.BuildGateClock
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
private data class InsertAppListingPayload(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("package_name") val packageName: String,
    val title: String,
    @SerialName("short_summary") val shortSummary: String,
    @SerialName("play_store_opt_in_url") val playStoreOptInUrl: String,
    @SerialName("google_group_url") val googleGroupUrl: String,
    @SerialName("deep_link_scheme") val deepLinkScheme: String? = null,
    @SerialName("current_build_version") val currentBuildVersion: String,
    @SerialName("min_version_code") val minVersionCode: Long,
    @SerialName("target_days") val targetDays: Int,
    @SerialName("target_testers") val targetTesters: Int,
    val category: String
)

@Serializable
private data class InsertMissionPayload(
    @SerialName("app_id") val appId: Long,
    @SerialName("build_version") val buildVersion: String,
    val title: String,
    val instructions: String,
    @SerialName("is_required") val isRequired: Boolean
)

@Serializable
private data class InsertFeedbackPayload(
    @SerialName("app_id") val appId: Long,
    @SerialName("author_id") val authorId: String,
    @SerialName("build_version") val buildVersion: String,
    val category: String,
    val title: String,
    val description: String,
    @SerialName("device_metadata") val deviceMetadata: DeviceMetadata,
    @SerialName("screenshot_path") val screenshotPath: String? = null,
    @SerialName("video_clip_path") val videoClipPath: String? = null,
    @SerialName("media_duration_ms") val mediaDurationMs: Int? = null
)

class SupabaseAuthRepository(
    private val client: SupabaseClient
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionState = MutableStateFlow(AuthSession(isLoggedIn = false, isDemoMode = false))
    private val ledgerCache = MutableStateFlow<List<KarmaEntry>>(emptyList())

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user != null) {
                            val profile = fetchProfile(user.id)
                            refreshLedger(user.id)
                            sessionState.value = AuthSession(
                                accessToken = status.session.accessToken,
                                user = AuthUser(
                                    id = user.id,
                                    email = user.email ?: "",
                                    isGoogleLinked = true
                                ),
                                profile = profile,
                                isLoggedIn = true,
                                isDemoMode = false
                            )
                        }
                    }
                    else -> {
                        sessionState.value = AuthSession(isLoggedIn = false, isDemoMode = false)
                    }
                }
            }
        }
    }

    private suspend fun fetchProfile(uid: String): TesterProfile? = runCatching {
        client.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<TesterProfile>()
    }.getOrNull()

    private suspend fun refreshLedger(uid: String) {
        runCatching {
            val rows = client.from("karma_ledger")
                .select {
                    filter { eq("user_id", uid) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<KarmaEntry>()
            ledgerCache.value = rows
        }
    }

    override fun getAuthSession(): Flow<AuthSession> = sessionState.asStateFlow()

    override fun getKarmaLedger(userId: String): Flow<List<KarmaEntry>> =
        ledgerCache.map { list -> list.filter { it.userId == userId } }

    override suspend fun checkDisplayNameAvailability(displayName: String): DisplayNameAvailability {
        val clean = displayName.trim()
        if (clean.length < 2) {
            return DisplayNameAvailability(clean, isAvailable = false, errorMessage = "Display name must be at least 2 characters.")
        }
        return runCatching {
            val json = client.postgrest.rpc(
                "check_display_name_availability",
                buildJsonObject { put("p_display_name", clean) }
            ).decodeAs<JsonObject>()
            val available = json["available"]?.jsonPrimitive?.booleanOrNull ?: false
            val err = json["error"]?.jsonPrimitive?.content
            val suggestions = (json["suggestions"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }
                ?: emptyList()
            DisplayNameAvailability(
                query = clean,
                isAvailable = available,
                suggestions = suggestions,
                errorMessage = err
            )
        }.getOrElse {
            DisplayNameAvailability(clean, isAvailable = true)
        }
    }

    override suspend fun signInWithGoogle(): Result<AuthSession> = runCatching {
        client.auth.signInWith(Google)
        sessionState.value
    }

    override suspend fun signInWithEmail(email: String, pass: String): Result<AuthSession> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = pass
        }
        sessionState.value
    }

    override suspend fun signUpWithEmail(email: String, pass: String, username: String): Result<AuthSession> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = pass
            this.data = buildJsonObject {
                put("username", username.trim())
            }
        }
        sessionState.value
    }

    override suspend fun registerCurrentDevice(device: DeviceMetadata) {
        val current = sessionState.value
        val profile = current.profile ?: return
        if (profile.registeredDevices.any { it.model == device.model && it.osVersion == device.osVersion }) return
        val updatedDevices = profile.registeredDevices + device
        runCatching {
            client.from("profiles").update(
                { set("registered_devices", updatedDevices) }
            ) {
                filter { eq("id", profile.id) }
            }
            sessionState.value = current.copy(profile = profile.copy(registeredDevices = updatedDevices))
        }
    }

    override suspend fun awardKarma(userId: String, delta: Int, reason: KarmaReason, appId: String?, note: String) {
        // In live mode, Karma is mutated server-side by complete_test_session / triage_feedback_with_bounty RPCs;
        // refresh profile & ledger from Postgres to stay strictly synchronized.
        val refreshed = fetchProfile(userId)
        refreshLedger(userId)
        if (refreshed != null && sessionState.value.user?.id == userId) {
            sessionState.value = sessionState.value.copy(profile = refreshed)
        }
    }

    override suspend fun handleAuthCallbackUri(uri: String): Boolean = runCatching {
        val query = uri.substringAfter("?", "")
        val code = query.split("&").firstOrNull { it.startsWith("code=") }?.substringAfter("code=")
        if (!code.isNullOrBlank()) {
            client.auth.exchangeCodeForSession(code)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
        sessionState.value = AuthSession(isLoggedIn = false, isDemoMode = false)
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        client.postgrest.rpc("delete_own_account")
        runCatching { client.auth.signOut() }
        ledgerCache.value = emptyList()
        sessionState.value = AuthSession(isLoggedIn = false, isDemoMode = false)
    }
}

class SupabaseAppListingRepository(
    private val client: SupabaseClient
) : AppListingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listingsState = MutableStateFlow<List<AppListing>>(emptyList())
    private val missionsState = MutableStateFlow<List<TestMission>>(emptyList())

    init {
        scope.launch { refreshAll() }
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated || status is SessionStatus.NotAuthenticated) {
                    refreshAll()
                }
            }
        }
    }

    suspend fun refreshAll() {
        runCatching {
            listingsState.value = client.from("app_discovery_feed")
                .select()
                .decodeList<AppListing>()
                .sortedByDescending { it.discoveryScore }
        }
        runCatching {
            missionsState.value = client.from("test_missions")
                .select()
                .decodeList<TestMission>()
        }
    }

    override fun getListings(): Flow<List<AppListing>> = listingsState.asStateFlow()

    override fun observeListingByIdOrPackage(idOrPackage: String): Flow<AppListing?> =
        listingsState.map { list -> list.find { it.id == idOrPackage || it.packageName == idOrPackage } }

    override suspend fun getListingById(id: String): AppListing? {
        if (listingsState.value.isEmpty()) refreshAll()
        return listingsState.value.find { it.id == id || it.packageName == id }
    }

    override suspend fun getListingByPackage(packageName: String): AppListing? {
        if (listingsState.value.isEmpty()) refreshAll()
        return listingsState.value.find { it.packageName == packageName }
    }

    override suspend fun createListing(listing: AppListing): AppListing {
        val created = client.from("app_listings").insert(
            InsertAppListingPayload(
                ownerId = listing.ownerId,
                packageName = listing.packageName,
                title = listing.title,
                shortSummary = listing.shortSummary,
                playStoreOptInUrl = listing.playStoreOptInUrl,
                googleGroupUrl = listing.googleGroupUrl,
                deepLinkScheme = listing.deepLinkScheme,
                currentBuildVersion = listing.currentBuildVersion,
                minVersionCode = listing.minVersionCode,
                targetDays = listing.targetDays,
                targetTesters = listing.targetTesters,
                category = listing.category
            )
        ) {
            select()
        }.decodeSingle<AppListing>()
        refreshAll()
        return created
    }

    override suspend fun updateListing(listing: AppListing): AppListing {
        val numericId = listing.id.toLongOrNull()
            ?: error("Cannot update listing without a valid numeric ID")
        val updated = client.from("app_listings").update({
            set("title", listing.title)
            set("short_summary", listing.shortSummary)
            set("play_store_opt_in_url", listing.playStoreOptInUrl)
            set("google_group_url", listing.googleGroupUrl)
            set("deep_link_scheme", listing.deepLinkScheme)
            set("current_build_version", listing.currentBuildVersion)
            set("min_version_code", listing.minVersionCode)
            set("target_days", listing.targetDays)
            set("target_testers", listing.targetTesters)
            set("category", listing.category)
        }) {
            filter { eq("id", numericId) }
            select()
        }.decodeSingle<AppListing>()
        refreshAll()
        return updated
    }

    override fun getMissionsForApp(appId: String): Flow<List<TestMission>> =
        missionsState.map { list -> list.filter { it.appId == appId } }

    override suspend fun createMission(mission: TestMission) {
        val numericAppId = mission.appId.toLongOrNull() ?: return
        client.from("test_missions").insert(
            InsertMissionPayload(
                appId = numericAppId,
                buildVersion = mission.buildVersion,
                title = mission.title,
                instructions = mission.instructions,
                isRequired = mission.isRequired
            )
        )
        refreshAll()
    }

    override suspend fun updateMission(mission: TestMission) {
        val numericId = mission.id.toLongOrNull() ?: return
        client.from("test_missions").update({
            set("title", mission.title)
            set("instructions", mission.instructions)
            set("build_version", mission.buildVersion)
            set("is_required", mission.isRequired)
        }) {
            filter { eq("id", numericId) }
        }
        refreshAll()
    }

    override suspend fun deleteMission(missionId: String) {
        val numericId = missionId.toLongOrNull() ?: return
        client.from("test_missions").delete {
            filter { eq("id", numericId) }
        }
        refreshAll()
    }

    override suspend fun toggleMissionCompletion(missionId: String) {
        var newCompleted = false
        missionsState.value = missionsState.value.map {
            if (it.id == missionId) {
                newCompleted = !it.isCompleted
                it.copy(isCompleted = newCompleted)
            } else it
        }
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val numericMissionId = missionId.toLongOrNull() ?: return
        runCatching {
            if (newCompleted) {
                client.from("mission_completions").upsert(
                    buildJsonObject {
                        put("mission_id", numericMissionId)
                        put("tester_id", uid)
                    }
                )
            } else {
                client.from("mission_completions").delete {
                    filter {
                        eq("mission_id", numericMissionId)
                        eq("tester_id", uid)
                    }
                }
            }
        }
    }

    override suspend fun spendKarmaForBoost(appId: String, boostType: AppBoostType): Result<Int> = runCatching {
        val numericAppId = appId.toLongOrNull() ?: error("Invalid app ID")
        val json = client.postgrest.rpc(
            "spend_karma_for_app_boost",
            buildJsonObject {
                put("p_app_id", numericAppId)
                put("p_boost_type", boostType.name)
            }
        ).decodeAs<JsonObject>()
        refreshAll()
        json["remaining_karma"]?.jsonPrimitive?.intOrNull ?: 0
    }
}

class SupabaseEnrollmentRepository(
    private val client: SupabaseClient
) : EnrollmentRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val enrollmentsState = MutableStateFlow<List<Enrollment>>(emptyList())
    private val verificationsState = MutableStateFlow<List<DailyVerification>>(emptyList())

    init {
        scope.launch { refresh() }
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    refresh()
                } else if (status is SessionStatus.NotAuthenticated) {
                    enrollmentsState.value = emptyList()
                    verificationsState.value = emptyList()
                }
            }
        }
    }

    private suspend fun refresh() {
        runCatching {
            enrollmentsState.value = client.from("enrollments").select().decodeList<Enrollment>()
        }
        runCatching {
            verificationsState.value = client.from("daily_verifications").select().decodeList<DailyVerification>()
        }
    }

    override fun getEnrollmentsForTester(testerId: String): Flow<List<Enrollment>> {
        if (testerId.isNotBlank()) scope.launch { refresh() }
        return enrollmentsState.map { list -> list.filter { it.testerId.equals(testerId, ignoreCase = true) } }
    }

    override fun getEnrollmentsForApp(appId: String): Flow<List<Enrollment>> {
        if (appId.isNotBlank()) scope.launch { refresh() }
        return enrollmentsState.map { list -> list.filter { it.appId == appId } }
    }

    override fun observeEnrollment(appId: String, testerId: String): Flow<Enrollment?> {
        if (appId.isNotBlank() && testerId.isNotBlank()) scope.launch { refresh() }
        return enrollmentsState.map { list ->
            list.find { it.appId == appId && it.testerId.equals(testerId, ignoreCase = true) }
        }
    }

    override suspend fun getEnrollment(appId: String, testerId: String): Enrollment? {
        if (enrollmentsState.value.isEmpty()) refresh()
        return enrollmentsState.value.find { it.appId == appId && it.testerId.equals(testerId, ignoreCase = true) }
    }

    override suspend fun enrollInApp(appId: String, testerId: String): Enrollment {
        val signedInUid = client.auth.currentUserOrNull()?.id
        if (signedInUid.isNullOrBlank()) {
            return Enrollment(
                id = "signed_out",
                appId = appId,
                testerId = testerId,
                status = EnrollmentStatus.PENDING_INSTALL,
                enrolledAtIso = BuildGateClock.nowIso()
            )
        }
        val existing = getEnrollment(appId, signedInUid)
        if (existing != null) return existing
        val numericAppId = appId.toLongOrNull() ?: return Enrollment(
            id = "pending",
            appId = appId,
            testerId = signedInUid,
            status = EnrollmentStatus.PENDING_INSTALL,
            enrolledAtIso = BuildGateClock.nowIso()
        )
        runCatching {
            client.postgrest.rpc(
                "start_test_session",
                buildJsonObject { put("p_app_id", numericAppId) }
            )
        }
        refresh()
        return getEnrollment(appId, signedInUid) ?: Enrollment(
            id = "pending",
            appId = appId,
            testerId = signedInUid,
            enrolledAtIso = BuildGateClock.nowIso()
        )
    }

    override suspend fun markOnboardingStep(
        appId: String,
        testerId: String,
        joinedGoogleGroup: Boolean?,
        acceptedPlayOptIn: Boolean?
    ): Enrollment {
        val current = enrollInApp(appId, testerId)
        runCatching {
            client.from("enrollments").update({
                joinedGoogleGroup?.let { set("has_joined_google_group", it) }
                acceptedPlayOptIn?.let { set("has_accepted_play_opt_in", it) }
            }) {
                filter { eq("id", current.id.toLongOrNull() ?: -1L) }
            }
        }
        refresh()
        return getEnrollment(appId, testerId) ?: current
    }

    override suspend fun syncInstallStatus(
        appId: String,
        testerId: String,
        isInstalled: Boolean,
        versionCode: Long?
    ): Enrollment? {
        val existing = getEnrollment(appId, testerId) ?: return null
        if (versionCode != null) {
            runCatching {
                client.from("enrollments").update({
                    set("last_seen_version_code", versionCode)
                }) {
                    filter { eq("id", existing.id.toLongOrNull() ?: -1L) }
                }
            }
        }
        refresh()
        return getEnrollment(appId, testerId)
    }

    override suspend fun recordDailyVerification(
        enrollmentId: String,
        sessionToken: String,
        activeDurationSeconds: Int,
        method: VerificationMethod,
        targetDays: Int,
        installedVersionCode: Long?
    ): VerificationOutcome {
        if (enrollmentsState.value.none { it.id == enrollmentId }) {
            refresh()
        }
        val enrollment = enrollmentsState.value.find { it.id == enrollmentId }
            ?: error("Enrollment not found")
        val numericAppId = enrollment.appId.toLongOrNull() ?: 1L
        val startJson = client.postgrest.rpc(
            "start_test_session",
            buildJsonObject { put("p_app_id", numericAppId) }
        ).decodeAs<JsonObject>()
        val serverNonce = startJson["session_nonce"]?.jsonPrimitive?.content ?: sessionToken

        val completeJson = client.postgrest.rpc(
            "complete_test_session",
            buildJsonObject {
                put("p_session_nonce", serverNonce)
                put("p_active_seconds", activeDurationSeconds.coerceAtLeast(60))
                installedVersionCode?.let { put("p_version_code", it) }
            }
        ).decodeAs<JsonObject>()

        refresh()
        val updated = enrollmentsState.value.find { it.id == enrollmentId } ?: enrollment
        return VerificationOutcome(
            updatedEnrollment = updated,
            karmaEarned = completeJson["karma_earned"]?.jsonPrimitive?.intOrNull ?: 10,
            dayNumber = completeJson["day_number"]?.jsonPrimitive?.intOrNull ?: updated.currentStreak,
            usedGraceDay = false,
            streakResetOccurred = false,
            campaignJustCompleted = completeJson["campaign_completed"]?.jsonPrimitive?.booleanOrNull ?: false,
            alreadyVerifiedToday = completeJson["already_verified_today"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }

    override fun getVerificationsForEnrollment(enrollmentId: String): Flow<List<DailyVerification>> =
        verificationsState.map { list -> list.filter { it.enrollmentId == enrollmentId } }

    override suspend fun purchaseGraceShield(enrollmentId: String): Result<Int> = runCatching {
        val numericId = enrollmentId.toLongOrNull() ?: error("Invalid enrollment ID")
        val json = client.postgrest.rpc(
            "purchase_grace_shield",
            buildJsonObject { put("p_enrollment_id", numericId) }
        ).decodeAs<JsonObject>()
        refresh()
        json["extra_grace_days"]?.jsonPrimitive?.intOrNull ?: 1
    }
}

class SupabaseFeedbackRepository(
    private val client: SupabaseClient
) : FeedbackRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val feedbackState = MutableStateFlow<List<FeedbackItem>>(emptyList())

    init {
        scope.launch { refresh() }
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated || status is SessionStatus.NotAuthenticated) {
                    refresh()
                }
            }
        }
    }

    private suspend fun refresh() {
        runCatching {
            feedbackState.value = client.from("feedback_items")
                .select { order("created_at", Order.DESCENDING) }
                .decodeList<FeedbackItem>()
        }
    }

    override fun getFeedbackForApp(appId: String): Flow<List<FeedbackItem>> {
        scope.launch { refresh() }
        return feedbackState.map { list -> list.filter { it.appId == appId } }
    }

    private suspend fun uploadMediaToR2OrSupabase(
        appId: String,
        authorId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): String? {
        val accessToken = client.auth.currentAccessTokenOrNull()
        if (!accessToken.isNullOrBlank()) {
            val r2Url = runCatching {
                val http = HttpClient {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 60_000L
                        connectTimeoutMillis = 15_000L
                        socketTimeoutMillis = 60_000L
                    }
                }
                try {
                    val response = http.post("https://buildgatehub.com/api/media/upload") {
                        parameter("appId", appId)
                        parameter("fileName", fileName)
                        header("Authorization", "Bearer $accessToken")
                        setBody(ByteArrayContent(bytes, ContentType.parse(mimeType)))
                    }
                    if (response.status.isSuccess()) {
                        val rawJson = response.body<String>()
                        kotlinx.serialization.json.Json.parseToJsonElement(rawJson)
                            .let { it as? JsonObject }
                            ?.get("url")?.jsonPrimitive?.content
                    } else {
                        println("BuildGate R2 upload failed: HTTP ${response.status.value} ${runCatching { response.body<String>() }.getOrDefault("")}")
                        null
                    }
                } finally {
                    http.close()
                }
            }.onFailure { err ->
                println("BuildGate R2 upload exception: ${err.message}")
            }.getOrNull()
            if (!r2Url.isNullOrBlank()) return r2Url
        }

        // Seamless fallback to Supabase Storage (non-fatal so the bug report itself still saves!)
        return runCatching {
            val bucket = client.storage.from("feedback-media")
            val path = "$authorId/$appId/${BuildGateClock.nowEpochMs()}_$fileName"
            bucket.upload(path, bytes) { upsert = true }
            bucket.publicUrl(path)
        }.onFailure { err ->
            println("BuildGate Supabase Storage fallback failed: ${err.message}")
        }.getOrNull()
    }

    override suspend fun submitFeedback(
        appId: String,
        authorId: String,
        authorUsername: String,
        sessionId: String?,
        buildVersion: String,
        category: FeedbackCategory,
        title: String,
        description: String,
        deviceMetadata: DeviceMetadata,
        screenshotBytes: ByteArray?,
        screenshotFileName: String?,
        videoClipBytes: ByteArray?,
        videoFileName: String?,
        mediaDurationMs: Int?,
        localPreviewUri: String?
    ): FeedbackItem {
        val uploadedScreenshotUrl = if (screenshotBytes != null) {
            val name = screenshotFileName ?: "screenshot.webp"
            val mime = if (name.endsWith(".png", ignoreCase = true)) "image/png" else "image/webp"
            uploadMediaToR2OrSupabase(appId, authorId, screenshotBytes, name, mime)
        } else null

        val uploadedVideoUrl = if (videoClipBytes != null) {
            val name = videoFileName ?: "clip.mp4"
            val mime = if (name.endsWith(".webm", ignoreCase = true)) "video/webm" else "video/mp4"
            uploadMediaToR2OrSupabase(appId, authorId, videoClipBytes, name, mime)
        } else null

        val created = client.from("feedback_items").insert(
            InsertFeedbackPayload(
                appId = appId.toLongOrNull() ?: 1L,
                authorId = authorId,
                buildVersion = buildVersion,
                category = category.name,
                title = title.trim(),
                description = description.trim(),
                deviceMetadata = deviceMetadata,
                screenshotPath = uploadedScreenshotUrl,
                videoClipPath = uploadedVideoUrl,
                mediaDurationMs = mediaDurationMs
            )
        ) {
            select()
        }.decodeSingle<FeedbackItem>()

        refresh()
        return created
    }

    override suspend fun updateFeedbackStatus(feedbackId: String, newStatus: FeedbackStatus): StatusUpdateResult {
        val numericId = feedbackId.toLongOrNull() ?: return StatusUpdateResult(null, null)
        val rawResult = client.postgrest.rpc(
            "triage_feedback_with_bounty",
            buildJsonObject {
                put("p_feedback_id", numericId)
                put("p_new_status", newStatus.name)
            }
        )
        val awarded = runCatching {
            rawResult.decodeAs<JsonObject>()["bounty_awarded"]?.jsonPrimitive?.booleanOrNull ?: false
        }.getOrDefault(false)
        refresh()
        val updated = feedbackState.value.find { it.id == feedbackId }
        return StatusUpdateResult(
            updatedItem = updated,
            newlyAwardedBountyToAuthorId = if (awarded) updated?.authorId else null
        )
    }

    override suspend fun upvoteFeedback(feedbackId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val numericId = feedbackId.toLongOrNull() ?: return
        runCatching {
            client.from("feedback_upvotes").insert(
                buildJsonObject {
                    put("feedback_id", numericId)
                    put("user_id", uid)
                }
            )
        }
        refresh()
    }
}
