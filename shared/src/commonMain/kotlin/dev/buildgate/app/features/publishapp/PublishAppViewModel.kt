package dev.buildgate.app.features.publishapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.testmission.model.TestMission
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeepLinkSnippetLanguage(val label: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    FLUTTER("Flutter"),
    REACT_NATIVE("React Native"),
    UNITY("Unity C#"),
    GODOT("Godot")
}

data class PublishAppUiState(
    val title: String = "",
    val packageName: String = "",
    val shortSummary: String = "",
    val googleGroupUrl: String = "https://groups.google.com/g/",
    val playStoreOptInUrl: String = "https://play.google.com/apps/testing/",
    val currentBuildVersion: String = "v1.0.0-rc1",
    val targetTesters: String = "16",
    val category: String = "Productivity",
    val enableDeepLinkHandshake: Boolean = false,
    val deepLinkScheme: String = "",
    val selectedSnippetLanguage: DeepLinkSnippetLanguage = DeepLinkSnippetLanguage.KOTLIN,
    val initialMissionTitle: String = "",
    val initialMissionInstructions: String = "",
    val isSubmitting: Boolean = false
) {
    /**
     * Unified, globally collision-free custom URI scheme derived automatically from the Android package ID
     * (valid RFC 3986 scheme syntax: lowercase letters, digits, dots, hyphens).
     * Example: com.mkdinteractive.shatterhorde -> buildgate.com.mkdinteractive.shatterhorde
     */
    val unifiedDefaultScheme: String
        get() {
            val cleanPkg = packageName.trim().lowercase().replace(Regex("[^a-z0-9.-]"), "")
            return if (cleanPkg.isNotBlank()) "buildgate.$cleanPkg" else "buildgate.com.yourcompany.app"
        }

    /** Active scheme used in snippets and published when [enableDeepLinkHandshake] is enabled. */
    val resolvedDeepLinkScheme: String
        get() = deepLinkScheme.trim().lowercase().ifBlank { unifiedDefaultScheme }

    val unifiedFullUriPreview: String
        get() = "$resolvedDeepLinkScheme://buildgate-verify?nonce=<one_time_token>"

    val adbTestCommand: String
        get() = "adb shell am start -W -a android.intent.action.VIEW -d \"$resolvedDeepLinkScheme://buildgate-verify?nonce=test_123\""

    val generatedSnippet: String
        get() = snippetFor(selectedSnippetLanguage)

    fun snippetFor(language: DeepLinkSnippetLanguage): String {
        val scheme = resolvedDeepLinkScheme
        val pkg = packageName.trim().ifBlank { "com.yourcompany.app" }
        val endpoint = "https://<PROJECT>.supabase.co/functions/v1/verify-deeplink-heartbeat"
        val manifestFilter = """
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="$scheme" android:host="buildgate-verify" />
</intent-filter>
        """.trimIndent()

        return when (language) {
            DeepLinkSnippetLanguage.KOTLIN -> """
<!-- 1. AndroidManifest.xml (inside your main <activity>) -->
$manifestFilter

// 2. MainActivity.kt (call from onCreate & onNewIntent):
intent?.data?.takeIf { it.scheme == "$scheme" && it.host == "buildgate-verify" }?.let { uri ->
    val nonce = uri.getQueryParameter("nonce") ?: return@let
    lifecycleScope.launch(Dispatchers.IO) {
        (URL("$endpoint").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.write("{\"nonce\":\"${'$'}nonce\",\"package_name\":\"$pkg\"}".toByteArray())
            inputStream.close()
        }
    }
}
            """.trimIndent()

            DeepLinkSnippetLanguage.JAVA -> """
<!-- 1. AndroidManifest.xml (inside your main <activity>) -->
$manifestFilter

// 2. MainActivity.java (call from onCreate & onNewIntent):
Uri uri = getIntent() != null ? getIntent().getData() : null;
if (uri != null && "$scheme".equals(uri.getScheme()) && "buildgate-verify".equals(uri.getHost())) {
    String nonce = uri.getQueryParameter("nonce");
    if (nonce != null) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("$endpoint").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.getOutputStream().write(("{\"nonce\":\"" + nonce + "\",\"package_name\":\"$pkg\"}").getBytes());
                c.getInputStream().close();
            } catch (Exception ignored) {}
        });
    }
}
            """.trimIndent()

            DeepLinkSnippetLanguage.FLUTTER -> """
<!-- 1. android/app/src/main/AndroidManifest.xml (inside MainActivity) -->
$manifestFilter

// 2. main.dart (pubspec: app_links; uses built-in dart:io HttpClient):
AppLinks().uriLinkStream.listen((uri) async {
  if (uri.scheme == '$scheme' && uri.host == 'buildgate-verify') {
    final nonce = uri.queryParameters['nonce'];
    if (nonce == null) return;
    final req = await HttpClient().postUrl(Uri.parse('$endpoint'));
    req.headers.contentType = ContentType.json;
    req.write(jsonEncode({'nonce': nonce, 'package_name': '$pkg'}));
    await (await req.close()).drain();
  }
});
            """.trimIndent()

            DeepLinkSnippetLanguage.REACT_NATIVE -> """
<!-- 1. AndroidManifest.xml (or Expo app.json: "scheme": "$scheme") -->
$manifestFilter

// 2. App.tsx (React Native / Expo):
import { Linking } from 'react-native';

const handleUrl = ({ url }: { url: string }) => {
  if (!url.startsWith('$scheme://buildgate-verify')) return;
  const nonce = new URL(url).searchParams.get('nonce');
  if (!nonce) return;
  fetch('$endpoint', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nonce, package_name: '$pkg' }),
  });
};
Linking.getInitialURL().then(url => url && handleUrl({ url }));
Linking.addEventListener('url', handleUrl);
            """.trimIndent()

            DeepLinkSnippetLanguage.UNITY -> """
<!-- 1. Assets/Plugins/Android/AndroidManifest.xml (inside UnityPlayerActivity) -->
$manifestFilter

// 2. BuildGateVerifier.cs (attach to a GameObject in your first scene):
void Awake() {
    Application.deepLinkActivated += OnDeepLink;
    if (!string.IsNullOrEmpty(Application.absoluteURL)) OnDeepLink(Application.absoluteURL);
}
void OnDeepLink(string url) {
    if (!url.StartsWith("$scheme://buildgate-verify")) return;
    var nonce = new Uri(url).Query.Split("nonce=")[1].Split('&')[0];
    StartCoroutine(PostHeartbeat(nonce));
}
IEnumerator PostHeartbeat(string nonce) {
    var json = $"{{\"nonce\":\"{nonce}\",\"package_name\":\"$pkg\"}}";
    using var req = UnityWebRequest.Post("$endpoint", json, "application/json");
    yield return req.SendWebRequest();
}
            """.trimIndent()

            DeepLinkSnippetLanguage.GODOT -> """
<!-- 1. Android export AndroidManifest.xml (inside GodotApp activity) -->
$manifestFilter

# 2. buildgate_verify.gd (autoload in Godot 4):
func _ready() -> void:
    for arg in OS.get_cmdline_args():
        if arg.begins_with("$scheme://buildgate-verify"):
            var nonce = arg.get_slice("nonce=", 1).get_slice("&", 0)
            var http = HTTPRequest.new()
            add_child(http)
            var body = JSON.stringify({"nonce": nonce, "package_name": "$pkg"})
            http.request("$endpoint", ["Content-Type: application/json"], HTTPClient.METHOD_POST, body)
            """.trimIndent()
        }
    }
}

sealed interface PublishAppEvent {
    data class ShowMessage(val msg: String) : PublishAppEvent
    data class Published(val packageName: String) : PublishAppEvent
}

class PublishAppViewModel(
    private val appListingRepository: AppListingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublishAppUiState())
    val uiState: StateFlow<PublishAppUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<PublishAppEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private val packageRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    fun onTitleChanged(v: String) { _uiState.update { it.copy(title = v) } }
    fun onPackageNameChanged(v: String) {
        val clean = v.trim()
        _uiState.update {
            it.copy(
                packageName = clean,
                playStoreOptInUrl = if (it.playStoreOptInUrl.startsWith("https://play.google.com/apps/testing/")) {
                    "https://play.google.com/apps/testing/$clean"
                } else it.playStoreOptInUrl
            )
        }
    }
    fun onSummaryChanged(v: String) { _uiState.update { it.copy(shortSummary = v) } }
    fun onGoogleGroupUrlChanged(v: String) { _uiState.update { it.copy(googleGroupUrl = v) } }
    fun onPlayOptInUrlChanged(v: String) { _uiState.update { it.copy(playStoreOptInUrl = v) } }
    fun onBuildVersionChanged(v: String) { _uiState.update { it.copy(currentBuildVersion = v) } }
    fun onTargetTestersChanged(v: String) { _uiState.update { it.copy(targetTesters = v) } }
    fun onCategoryChanged(v: String) { _uiState.update { it.copy(category = v) } }
    fun onToggleDeepLinkHandshake(enabled: Boolean) { _uiState.update { it.copy(enableDeepLinkHandshake = enabled) } }
    fun onDeepLinkSchemeChanged(v: String) { _uiState.update { it.copy(deepLinkScheme = v.lowercase().trim()) } }
    fun onSnippetLanguageSelected(lang: DeepLinkSnippetLanguage) { _uiState.update { it.copy(selectedSnippetLanguage = lang) } }
    fun onInitialMissionTitleChanged(v: String) { _uiState.update { it.copy(initialMissionTitle = v) } }
    fun onInitialMissionInstructionsChanged(v: String) { _uiState.update { it.copy(initialMissionInstructions = v) } }

    fun publishCampaign(ownerId: String, ownerKarma: Int) {
        val s = _uiState.value
        val pkg = s.packageName.trim()
        if (s.title.isBlank() || !packageRegex.matches(pkg)) {
            viewModelScope.launch {
                _eventChannel.send(PublishAppEvent.ShowMessage("Enter a valid app title and reverse-domain package name (e.g. io.studio.myapp)"))
            }
            return
        }
        if (!s.googleGroupUrl.startsWith("https://") || !s.playStoreOptInUrl.startsWith("https://")) {
            viewModelScope.launch {
                _eventChannel.send(PublishAppEvent.ShowMessage("Both Google Group URL and Play Opt-In URL must start with https://"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val created = runCatching {
                appListingRepository.createListing(
                    AppListing(
                        id = "",
                        ownerId = ownerId,
                        packageName = pkg,
                        title = s.title.trim(),
                        shortSummary = s.shortSummary.trim().ifBlank { "14-day closed testing campaign on BuildGate" },
                        playStoreOptInUrl = s.playStoreOptInUrl.trim(),
                        googleGroupUrl = s.googleGroupUrl.trim(),
                        deepLinkScheme = if (s.enableDeepLinkHandshake) s.resolvedDeepLinkScheme else null,
                        currentBuildVersion = s.currentBuildVersion.trim().ifBlank { "v1.0.0" },
                        targetTesters = (s.targetTesters.toIntOrNull() ?: 16).coerceAtLeast(12),
                        ownerKarmaBalance = ownerKarma,
                        category = s.category.trim().ifBlank { "Productivity" },
                        createdAtIso = BuildGateClock.nowIso()
                    )
                )
            }.getOrElse { err ->
                _uiState.update { it.copy(isSubmitting = false) }
                _eventChannel.send(PublishAppEvent.ShowMessage(err.message ?: "Failed to publish campaign"))
                return@launch
            }

            if (s.initialMissionTitle.isNotBlank()) {
                appListingRepository.createMission(
                    TestMission(
                        id = "m_${(1000..9999).random()}",
                        appId = created.id,
                        buildVersion = created.currentBuildVersion,
                        title = s.initialMissionTitle.trim(),
                        instructions = s.initialMissionInstructions.trim().ifBlank { "Test core flow and report any UX friction." },
                        isRequired = true
                    )
                )
            }

            _uiState.value = PublishAppUiState()
            _eventChannel.send(PublishAppEvent.ShowMessage("Published ${created.title}!"))
            _eventChannel.send(PublishAppEvent.Published(created.packageName))
        }
    }
}
