package life.absinthe.pixelota

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

internal data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val release: String,
    val device: String,
    val buildId: String,
    val securityPatch: String,
    val isPixel: Boolean,
)

internal data class OtaSectionUi(
    @param:StringRes val titleRes: Int,
    val entry: OtaCatalog.Entry?,
    @param:StringRes val noteRes: Int,
    @param:StringRes val emptyMessageRes: Int,
    val emptyArgs: List<Any> = emptyList(),
    val isWarningNote: Boolean,
)

internal enum class QueryTone {
    Idle,
    Loading,
    Success,
    Warning,
    Error,
}

internal sealed interface UiStatus {
    data object Idle : UiStatus
    data class NonPixel(val manufacturer: String, val device: String) : UiStatus
    data object Loading : UiStatus
    data class Checked(val timeLabel: String, val complete: Boolean) : UiStatus
    data class Failed(val errorName: String) : UiStatus
    data class Custom(val text: String) : UiStatus
}

internal data class PixelOtaUiState(
    val device: DeviceSnapshot,
    val termsAccepted: Boolean,
    val showTermsDialog: Boolean = false,
    val busy: Boolean = false,
    val hasQueriedOnce: Boolean = false,
    val status: UiStatus = UiStatus.Idle,
    val statusTone: QueryTone = QueryTone.Idle,
    val sections: List<OtaSectionUi> = emptyList(),
    val warnings: List<OtaRepository.Warning> = emptyList(),
)

internal fun isSupportedPixelDevice(
    manufacturer: String,
    model: String,
    device: String,
    fingerprint: String,
): Boolean = "Google".equals(manufacturer, ignoreCase = true) &&
    model.startsWith("Pixel") &&
    !device.startsWith("generic") &&
    !device.startsWith("emu") &&
    !fingerprint.contains("generic")

internal fun buildOtaSections(
    result: OtaRepository.Result,
    installedBuildId: String,
): List<OtaSectionUi> = listOf(
    OtaSectionUi(
        titleRes = R.string.ota_section_current_title,
        entry = result.current,
        noteRes = if (result.cachedCurrent) {
            R.string.ota_note_cached
        } else {
            R.string.ota_note_exact_build
        },
        emptyMessageRes = R.string.ota_empty_current,
        emptyArgs = listOf(installedBuildId),
        isWarningNote = result.cachedCurrent,
    ),
    OtaSectionUi(
        titleRes = if (result.betaComplete) {
            R.string.ota_section_beta_latest
        } else {
            R.string.ota_section_beta_found
        },
        entry = result.latest,
        noteRes = if (result.betaComplete) {
            R.string.ota_note_beta_complete
        } else {
            R.string.ota_note_beta_partial
        },
        emptyMessageRes = if (result.betaComplete) {
            R.string.ota_empty_beta_complete
        } else {
            R.string.ota_empty_beta_partial
        },
        isWarningNote = !result.betaComplete,
    ),
)

internal fun buildUiStatus(
    result: OtaRepository.Result,
    timeLabel: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
): UiStatus.Checked = UiStatus.Checked(
    timeLabel = timeLabel,
    complete = result.warnings.isEmpty(),
)

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val initialDevice by lazy {
        DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            device = Build.DEVICE,
            buildId = Build.ID,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            isPixel = isSupportedPixelDevice(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                fingerprint = Build.FINGERPRINT,
            ),
        )
    }

    private var uiState by mutableStateOf(
        PixelOtaUiState(
            device = DeviceSnapshot("", "", "", "", "", "", isPixel = false),
            termsAccepted = false,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val termsAccepted = getPreferences(MODE_PRIVATE).getBoolean("terms", false)
        val device = initialDevice
        uiState = if (!device.isPixel) {
            PixelOtaUiState(
                device = device,
                termsAccepted = termsAccepted,
                status = UiStatus.NonPixel(device.manufacturer, device.device),
                statusTone = QueryTone.Error,
            )
        } else {
            PixelOtaUiState(
                device = device,
                termsAccepted = termsAccepted,
                showTermsDialog = !termsAccepted,
            )
        }

        if (device.isPixel && termsAccepted) {
            query()
        }

        setContent {
            val darkMode = isSystemInDarkTheme()
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose {}
            }

            PixelOtaTheme {
                PixelOtaScreen(
                    state = uiState,
                    onQuery = ::query,
                    onShowTerms = { uiState = uiState.copy(showTermsDialog = true) },
                    onDismissTerms = { uiState = uiState.copy(showTermsDialog = false) },
                    onAcceptTerms = ::acceptTermsAndQuery,
                    onCopyUrl = ::copyEntryUrl,
                    onOpenUrl = ::openUrl,
                )
            }
        }
    }

    private fun acceptTermsAndQuery() {
        getPreferences(MODE_PRIVATE).edit { putBoolean("terms", true) }
        uiState = uiState.copy(termsAccepted = true, showTermsDialog = false)
        if (uiState.device.isPixel) {
            query()
        }
    }

    private fun query() {
        if (uiState.busy || !uiState.device.isPixel) return
        if (!getPreferences(MODE_PRIVATE).getBoolean("terms", false)) {
            uiState = uiState.copy(showTermsDialog = true)
            return
        }
        uiState = uiState.copy(
            busy = true,
            status = UiStatus.Loading,
            statusTone = QueryTone.Loading,
            sections = emptyList(),
            warnings = emptyList(),
        )
        worker.execute {
            try {
                val result = OtaRepository().query(
                    Build.DEVICE,
                    Build.ID,
                    File(filesDir, "ota-index.properties"),
                )
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    render(result)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (ex: RuntimeException) {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    uiState = uiState.copy(
                        busy = false,
                        hasQueriedOnce = true,
                        status = UiStatus.Failed(ex.javaClass.simpleName),
                        statusTone = QueryTone.Error,
                    )
                }
            }
        }
    }

    // Package-visible so the device UI check can exercise real result states without changing device identity.
    internal fun render(result: OtaRepository.Result, statusOverride: String? = null) {
        val hasWarnings = result.warnings.isNotEmpty() || !result.betaComplete || result.cachedCurrent
        uiState = uiState.copy(
            busy = false,
            hasQueriedOnce = true,
            status = if (statusOverride != null) UiStatus.Custom(statusOverride) else buildUiStatus(result),
            statusTone = if (hasWarnings) QueryTone.Warning else QueryTone.Success,
            sections = buildOtaSections(result, uiState.device.buildId),
            warnings = result.warnings.toList(),
        )
    }

    fun render(result: OtaRepository.Result) {
        render(result, null)
    }

    private fun copyEntryUrl(entry: OtaCatalog.Entry): Boolean {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.ota_clip_label, entry.build), entry.url))
        return true
    }

    private fun openUrl(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

@Composable
private fun PixelOtaTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colors = remember(isDark) {
        if (isDark) {
            darkColorScheme(
                primary = Color(0xFF4CC395),
                onPrimary = Color(0xFF003827),
                primaryVariant = Color(0xFF216A52),
                onPrimaryVariant = Color(0xFFBBEAD5),
                disabledPrimary = Color(0xFF1E4436),
                disabledOnPrimary = Color(0xFF6B8F80),
                disabledPrimaryButton = Color(0xFF1E4436),
                disabledOnPrimaryButton = Color(0xFF6B8F80),
                disabledPrimarySlider = Color(0xFF2B5948),
                primaryContainer = Color(0xFF1B332A),
                onPrimaryContainer = Color(0xFFD5F0E1),
                tertiaryContainer = Color(0xFF1D382D),
                onTertiaryContainer = Color(0xFF7BE0B7),
                tertiaryContainerVariant = Color(0xFF28473B),
                onBackgroundVariant = Color(0xFF8AA397),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF216A52),
                onPrimary = Color.White,
                primaryVariant = Color(0xFF216A52),
                onPrimaryVariant = Color(0xFFD0EAD9),
                disabledPrimary = Color(0xFFB7D6C8),
                disabledOnPrimary = Color(0xFFF3F8F5),
                disabledPrimaryButton = Color(0xFFB7D6C8),
                disabledOnPrimaryButton = Color.White,
                disabledPrimarySlider = Color(0xFFA9CEBE),
                primaryContainer = Color(0xFFE1EDE2),
                onPrimaryContainer = Color(0xFF1B2C25),
                tertiaryContainer = Color(0xFFE3F1E8),
                onTertiaryContainer = Color(0xFF216A52),
                tertiaryContainerVariant = Color(0xFFE3F1E8),
                onBackgroundVariant = Color(0xFF526359),
            )
        }
    }
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onBackground,
            content = content,
        )
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun PixelOtaScreen(
    state: PixelOtaUiState,
    onQuery: () -> Unit,
    onShowTerms: () -> Unit,
    onDismissTerms: () -> Unit,
    onAcceptTerms: () -> Unit,
    onCopyUrl: (OtaCatalog.Entry) -> Boolean,
    onOpenUrl: (String) -> Boolean,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val lazyListState = rememberLazyListState()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if (isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }

    val noBrowserMessage = stringResource(R.string.error_no_browser)
    val openUriWithFeedback: (String) -> Unit = { url ->
        if (!onOpenUrl(url)) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(noBrowserMessage, duration = SnackbarDuration.Short)
            }
        }
    }

    val menuTermsLabel = stringResource(R.string.menu_terms)
    val menuBetaEntryLabel = stringResource(R.string.menu_beta_entry)
    val menuStableCatalogLabel = stringResource(R.string.menu_stable_catalog)
    val copiedMessage = stringResource(R.string.ota_copy_toast)
    val menuEntry = remember(menuTermsLabel, menuBetaEntryLabel, menuStableCatalogLabel) {
        DropdownEntry(
            items = listOf(
                DropdownItem(text = menuTermsLabel, onClick = onShowTerms),
                DropdownItem(text = menuBetaEntryLabel, onClick = { openUriWithFeedback(OtaUrls.VERSIONS) }),
                DropdownItem(text = menuStableCatalogLabel, onClick = { openUriWithFeedback(OtaUrls.STABLE) }),
            ),
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredTopBar(backdrop = backdrop, scrollBehavior = topAppBarScrollBehavior) {
                TopAppBar(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.top_bar_subtitle),
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = {
                        val requeryLabel = stringResource(R.string.action_requery)
                        IconButton(
                            onClick = onQuery,
                            enabled = state.device.isPixel && !state.busy,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = requeryLabel,
                            )
                        }
                        val moreLabel = stringResource(R.string.action_more)
                        OverlayIconDropdownMenu(entry = menuEntry) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = moreLabel,
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val navPadding = WindowInsets.navigationBars.asPaddingValues()
        val captionPadding = WindowInsets.captionBar.asPaddingValues()
        val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            start = cutoutPadding.calculateLeftPadding(layoutDirection),
            end = cutoutPadding.calculateRightPadding(layoutDirection),
            bottom = navPadding.calculateBottomPadding() + captionPadding.calculateBottomPadding() + 20.dp,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            PullToRefresh(
                isRefreshing = state.busy,
                onRefresh = onQuery,
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                contentPadding = contentPadding,
                color = MiuixTheme.colorScheme.primary,
                refreshTexts = listOf(
                    stringResource(R.string.pull_refresh_pulling),
                    stringResource(R.string.pull_refresh_release),
                    stringResource(R.string.pull_refresh_refreshing),
                    stringResource(R.string.pull_refresh_complete),
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                        contentPadding = contentPadding,
                    ) {
                        item(key = "device") {
                            DeviceSummaryCard(
                                state = state,
                                onQuery = onQuery,
                            )
                        }

                        state.sections.forEach { section ->
                            item(key = section.titleRes) {
                                OtaResultSection(
                                    section = section,
                                    onCopy = { entry ->
                                        if (onCopyUrl(entry)) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = copiedMessage,
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }
                                    },
                                    onOpenSource = openUriWithFeedback,
                                )
                            }
                        }

                        if (state.warnings.isNotEmpty()) {
                            item(key = "warnings") {
                                WarningsSection(warnings = state.warnings)
                            }
                        }

                        item(key = "footer") {
                            FooterSection(
                                termsAccepted = state.termsAccepted,
                                onShowTerms = onShowTerms,
                            )
                        }
                    }

                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(lazyListState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        trackPadding = contentPadding,
                    )
                }
            }
        }

        OverlayDialog(
            show = state.showTermsDialog,
            title = stringResource(R.string.dialog_terms_title),
            summary = stringResource(R.string.dialog_terms_summary),
            onDismissRequest = onDismissTerms,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = stringResource(R.string.dialog_terms_view_official),
                    onClick = { openUriWithFeedback(OtaUrls.STABLE_LEGAL) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.dialog_terms_later),
                        onClick = onDismissTerms,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.dialog_terms_agree),
                        onClick = onAcceptTerms,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlurredTopBar(
    backdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    Box {
        if (backdrop != null) {
            val blurColors = BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(color = surfaceColor.copy(alpha = 0.68f)),
                ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val extra = 24.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = constraints.maxHeight + extra,
                                maxHeight = constraints.maxHeight + extra,
                            ),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .graphicsLayer {
                        alpha = (-scrollBehavior.state.contentOffset / 36.dp.toPx()).coerceIn(0f, 1f)
                    }
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 60f,
                        gradient = ProgressiveBlur(angle = 90f, startFraction = 0.25f, endFraction = 1f, curve = 0.65f),
                        colors = blurColors,
                    ),
            )
        }
        content()
    }
}

@Composable
private fun DeviceSummaryCard(
    state: PixelOtaUiState,
    onQuery: () -> Unit,
) {
    val device = state.device
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer,
            contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
        ),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Phone,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.model,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.device_android_release_format, device.release, device.device),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
            StatusPill(
                text = stringResource(
                    if (device.isPixel) R.string.device_badge_pixel else R.string.device_badge_non_pixel,
                ),
                containerColor = if (device.isPixel) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MiuixTheme.colorScheme.errorContainer
                },
                contentColor = if (device.isPixel) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.68f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.device_label_build_id),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                    Text(
                        text = device.buildId,
                        style = MiuixTheme.textStyles.headline2,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.device_label_security_patch),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = device.securityPatch,
                        style = MiuixTheme.textStyles.footnote1,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onQuery,
            enabled = device.isPixel && !state.busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            AnimatedVisibility(visible = state.busy) {
                InfiniteProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    size = 18.dp,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
            if (!state.busy) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                )
            }
            Text(
                text = when {
                    state.busy -> stringResource(R.string.action_querying)
                    state.hasQueriedOnce -> stringResource(R.string.action_requery)
                    else -> stringResource(R.string.action_query)
                },
                style = MiuixTheme.textStyles.button,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dotColor = when (state.statusTone) {
                QueryTone.Idle -> MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f)
                QueryTone.Loading, QueryTone.Success -> MiuixTheme.colorScheme.primary
                QueryTone.Warning, QueryTone.Error -> MiuixTheme.colorScheme.error
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = resolveStatusText(state.status),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }

        AnimatedVisibility(visible = state.busy) {
            LinearProgressIndicator(
                modifier = Modifier.padding(top = 10.dp),
                progress = null,
            )
        }
    }
}

@Composable
private fun resolveStatusText(status: UiStatus): String = when (status) {
    UiStatus.Idle -> stringResource(R.string.status_idle)
    is UiStatus.NonPixel -> stringResource(R.string.status_non_pixel, status.manufacturer, status.device)
    UiStatus.Loading -> stringResource(R.string.status_loading)
    is UiStatus.Checked -> stringResource(
        if (status.complete) R.string.status_checked_complete else R.string.status_checked_partial,
        status.timeLabel,
    )
    is UiStatus.Failed -> stringResource(R.string.query_failed_format, status.errorName)
    is UiStatus.Custom -> status.text
}

@Composable
private fun OtaResultSection(
    section: OtaSectionUi,
    onCopy: (OtaCatalog.Entry) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val titleText = stringResource(section.titleRes)
    SmallTitle(text = titleText)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        val entry = section.entry
        if (entry == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Info,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.ota_empty_badge),
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = if (section.emptyArgs.isEmpty()) {
                        stringResource(section.emptyMessageRes)
                    } else {
                        stringResource(section.emptyMessageRes, *section.emptyArgs.toTypedArray())
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatusPill(
                        text = stringResource(
                            if (entry.beta) R.string.ota_badge_beta else R.string.ota_badge_stable,
                        ),
                        containerColor = if (section.isWarningNote) {
                            MiuixTheme.colorScheme.errorContainer
                        } else {
                            MiuixTheme.colorScheme.tertiaryContainer
                        },
                        contentColor = if (section.isWarningNote) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onTertiaryContainer
                        },
                    )
                    if (entry.date != null) {
                        Text(
                            text = stringResource(R.string.ota_released_date, entry.date.toString()),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                SelectionContainer {
                    Text(
                        text = entry.build,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(section.noteRes),
                    style = MiuixTheme.textStyles.body2,
                    color = if (section.isWarningNote) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )

                Spacer(Modifier.height(12.dp))

                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.ota_label_filename),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                            Text(
                                text = entry.filename(),
                                style = MiuixTheme.textStyles.footnote1,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.ota_label_sha256),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                            Text(
                                text = entry.sha256,
                                style = MiuixTheme.textStyles.footnote1,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                val copyContentDescription = stringResource(R.string.ota_copy_content_desc, titleText)
                Button(
                    onClick = { onCopy(entry) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = copyContentDescription },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.ota_action_copy),
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ArrowPreference(
                title = stringResource(R.string.ota_action_source),
                summary = OtaUrls.displayUrl(entry.source),
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.Link,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(20.dp),
                    )
                },
                onClick = { onOpenSource(entry.source) },
            )
        }
    }
}

@Composable
private fun resolveFetchErrorText(error: OtaRepository.FetchError): String = when (error) {
    OtaRepository.FetchError.NonOfficialRedirect -> stringResource(R.string.error_fetch_non_official_redirect)
    OtaRepository.FetchError.MissingLocation -> stringResource(R.string.error_fetch_missing_location)
    OtaRepository.FetchError.Cancelled -> stringResource(R.string.error_fetch_cancelled)
    OtaRepository.FetchError.PageTooLarge -> stringResource(R.string.error_fetch_too_large)
    OtaRepository.FetchError.TooManyRedirects -> stringResource(R.string.error_fetch_too_many_redirects)
    is OtaRepository.FetchError.HttpStatus -> stringResource(R.string.error_fetch_http_status, error.code)
    is OtaRepository.FetchError.Other -> error.message
}

@Composable
private fun resolveWarningText(warning: OtaRepository.Warning): String = when (warning) {
    OtaRepository.Warning.NoBetaEntry -> stringResource(R.string.warning_no_beta_entry)
    OtaRepository.Warning.PendingSources -> stringResource(R.string.warning_pending_sources)
    is OtaRepository.Warning.EmptyPage -> stringResource(R.string.warning_empty_page, warning.url)
    OtaRepository.Warning.NoDeviceBeta -> stringResource(R.string.warning_no_device_beta)
    OtaRepository.Warning.CacheReadFailed -> stringResource(R.string.warning_cache_read)
    OtaRepository.Warning.CacheWriteFailed -> stringResource(R.string.warning_cache_write)
    is OtaRepository.Warning.FetchFailed -> stringResource(
        R.string.warning_fetch_failed,
        warning.source,
        resolveFetchErrorText(warning.error),
    )
}

@Composable
private fun WarningsSection(warnings: List<OtaRepository.Warning>) {
    SmallTitle(text = stringResource(R.string.section_warnings))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
        insideMargin = PaddingValues(16.dp),
    ) {
        val text = warnings.map { resolveWarningText(it) }.joinToString("\n")
        SelectionContainer {
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun FooterSection(
    termsAccepted: Boolean,
    onShowTerms: () -> Unit,
) {
    SmallTitle(text = stringResource(R.string.section_about))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        BasicComponent(
            title = stringResource(R.string.about_disclaimer_title),
            summary = stringResource(R.string.about_disclaimer_summary),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(20.dp),
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ArrowPreference(
            title = stringResource(R.string.menu_terms),
            summary = stringResource(
                if (termsAccepted) {
                    R.string.about_terms_summary_accepted
                } else {
                    R.string.about_terms_summary_pending
                },
            ),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.FileDownloads,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(20.dp),
                )
            },
            onClick = onShowTerms,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
