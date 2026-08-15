package com.lumocraft.app

import android.app.Application
import com.lumocraft.app.data.account.SharedPreferencesAccountRepository
import com.lumocraft.app.data.export.CrashLogHandler
import com.lumocraft.app.data.export.CrashReportExporter
import com.lumocraft.app.data.input.AndroidControllerManager
import com.lumocraft.app.data.input.AndroidKeyboardManager
import com.lumocraft.app.data.input.AndroidTouchEventMapper
import com.lumocraft.app.data.input.DefaultInputManager
import com.lumocraft.app.data.input.DefaultVirtualMouseManager
import com.lumocraft.app.data.input.InputPreferences
import com.lumocraft.app.data.input.JsonInputRepository
import com.lumocraft.app.data.launch.ClasspathBuilder
import com.lumocraft.app.data.launch.ClientJarManager
import com.lumocraft.app.data.launch.CrashAnalyzer
import com.lumocraft.app.data.launch.DefaultLaunchPipeline
import com.lumocraft.app.data.launch.GameSurfaceGate
import com.lumocraft.app.data.launch.LaunchArgumentBuilder
import com.lumocraft.app.data.launch.LaunchEnvironment
import com.lumocraft.app.data.launch.LauncherLogRepository
import com.lumocraft.app.data.launch.LaunchValidator
import com.lumocraft.app.data.launch.NativeJvmLauncher
import com.lumocraft.app.data.launch.PojavLwjglInstaller
import com.lumocraft.app.data.loader.CompositeLoaderLaunchConfigurator
import com.lumocraft.app.data.loader.DefaultLoaderRepository
import com.lumocraft.app.data.loader.FabricInstaller
import com.lumocraft.app.data.loader.FabricLaunchConfigurator
import com.lumocraft.app.data.loader.FabricMetadataService
import com.lumocraft.app.data.loader.LoaderScanner
import com.lumocraft.app.data.native.DefaultNativeRuntimeManager
import com.lumocraft.app.data.native.NativeArchitecture
import com.lumocraft.app.data.native.NativeExtractionService
import com.lumocraft.app.data.native.NativeLibraryManager
import com.lumocraft.app.data.native.NativeVerificationService
import com.lumocraft.app.data.network.DownloadScheduler
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.data.network.ThroughputTracker
import com.lumocraft.app.data.performance.AndroidDeviceProfiler
import com.lumocraft.app.data.performance.ChecksumCache
import com.lumocraft.app.data.performance.DefaultPerformanceManager
import com.lumocraft.app.data.performance.JsonCacheManager
import com.lumocraft.app.data.performance.LaunchProfilerImpl
import com.lumocraft.app.data.performance.MemoryOptimizerImpl
import com.lumocraft.app.data.performance.PerformancePreference
import com.lumocraft.app.data.performance.RuntimeCache
import com.lumocraft.app.data.performance.SmartVerifierImpl
import com.lumocraft.app.data.preferences.RendererPreference
import com.lumocraft.app.data.preferences.VersionPreference
import com.lumocraft.app.data.runtime.ArchiveExtractor
import com.lumocraft.app.data.runtime.DefaultRuntimeRepository
import com.lumocraft.app.data.runtime.RuntimeInstaller
import com.lumocraft.app.data.runtime.RuntimeVerifier
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.update.GithubUpdateRepository
import com.lumocraft.app.data.version.AssetInstaller
import com.lumocraft.app.data.version.DefaultVersionRepository
import com.lumocraft.app.data.version.LibraryInstaller
import com.lumocraft.app.data.version.ManifestService
import com.lumocraft.app.data.version.VerificationService
import com.lumocraft.app.data.version.VersionInstaller
import com.lumocraft.app.domain.account.AccountRepository
import com.lumocraft.app.domain.input.InputManager
import com.lumocraft.app.domain.input.InputRepository
import com.lumocraft.app.domain.launch.LaunchContext
import com.lumocraft.app.domain.launch.LaunchPipeline
import com.lumocraft.app.domain.loader.LoaderLaunchConfigurator
import com.lumocraft.app.domain.loader.LoaderRepository
import com.lumocraft.app.domain.native.NativeRuntimeManager
import com.lumocraft.app.domain.performance.MemoryOptimizer
import com.lumocraft.app.domain.performance.PerformanceManager
import com.lumocraft.app.domain.runtime.RuntimeRepository
import com.lumocraft.app.domain.update.UpdateRepository
import com.lumocraft.app.domain.version.VersionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.utils.JREUtils

/**
 * Minimal manual dependency container — no DI framework needed.
 * Repositories are created once and shared through the app; ViewModels
 * resolve them via their factories.
 */
class LumoCraftApplication : Application() {

    val accountRepository: AccountRepository by lazy {
        SharedPreferencesAccountRepository(this)
    }

    /** Persists which installed version the Home screen launches. */
    val versionPreference: VersionPreference by lazy {
        VersionPreference(this)
    }

    val storageManager: StorageManager by lazy {
        StorageManager(this)
    }

    /** Shared bandwidth estimator for adaptive download concurrency. */
    private val throughputTracker: ThroughputTracker by lazy {
        ThroughputTracker()
    }

    /** Shared adaptive concurrency scheduler (device tier + bandwidth). */
    private val downloadScheduler: DownloadScheduler by lazy {
        DownloadScheduler(deviceProfiler::detect, throughputTracker)
    }

    /** Cached runtime validation (skips repeated scans). */
    private val runtimeCache: RuntimeCache by lazy {
        RuntimeCache(storageManager)
    }

    /** Pooled byte buffers released after each launch. */
    val memoryOptimizer: MemoryOptimizer by lazy {
        MemoryOptimizerImpl()
    }

    val deviceProfiler: AndroidDeviceProfiler by lazy {
        AndroidDeviceProfiler(this)
    }

    /** Phase 9 entry point for all launcher-side optimization. */
    val performanceManager: PerformanceManager by lazy {
        val storage = storageManager
        val cache = JsonCacheManager(storage)
        DefaultPerformanceManager(
            profiler = deviceProfiler,
            preference = PerformancePreference(this),
            cache = cache,
            verifier = SmartVerifierImpl(
                storage = storage,
                cache = cache,
                checksums = ChecksumCache(storage),
                buffers = memoryOptimizer
            ),
            launchProfiler = LaunchProfilerImpl(storage),
            memory = memoryOptimizer,
            runtimeRepository = runtimeRepository,
            storage = storage,
            scheduler = downloadScheduler
        )
    }

    /**
     * Concrete repository instance (kept private): exposes startup
     * recovery of interrupted installs before the UI reads install states.
     */
    private val defaultVersionRepository: DefaultVersionRepository by lazy {
        val client = HttpClient()
        val storage = storageManager
        val downloader = Downloader(client, throughput = throughputTracker, logs = launcherLogRepository)
        DefaultVersionRepository(
            manifestService = ManifestService(client),
            installer = VersionInstaller(
                storage = storage,
                downloader = downloader,
                libraryInstaller = LibraryInstaller(storage, downloader, downloadScheduler),
                assetInstaller = AssetInstaller(storage, downloader, downloadScheduler),
                verificationService = VerificationService(storage),
                onFilesChanged = { versionId ->
                    performanceManager.cache().removeEntry(versionId)
                    performanceManager.verifier().invalidate(versionId)
                },
                logs = launcherLogRepository
            ),
            storage = storage
        )
    }

    val versionRepository: VersionRepository by lazy {
        defaultVersionRepository
    }

    val loaderRepository: LoaderRepository by lazy {
        val client = HttpClient()
        val storage = storageManager
        val downloader = Downloader(client, throughput = throughputTracker, logs = launcherLogRepository)
        val verificationService = VerificationService(storage)
        val metadataService = FabricMetadataService(client, storage)
        val libraryInstaller = LibraryInstaller(storage, downloader, downloadScheduler)
        val onFilesChanged: suspend (String) -> Unit = { versionId ->
            performanceManager.cache().removeEntry(versionId)
            performanceManager.verifier().invalidate(versionId)
        }
        DefaultLoaderRepository(
            storage = storage,
            scanner = LoaderScanner(storage, verificationService),
            sources = listOf(metadataService),
            installers = listOf(
                FabricInstaller(
                    storage = storage,
                    downloader = downloader,
                    metadataService = metadataService,
                    libraryInstaller = libraryInstaller,
                    verificationService = verificationService,
                    onFilesChanged = onFilesChanged
                )
            ),
            onFilesChanged = onFilesChanged
        )
    }

    /** Generic loader integration point consumed by the launch pipeline. */
    val loaderLaunchConfigurator: LoaderLaunchConfigurator by lazy {
        CompositeLoaderLaunchConfigurator(
            configurators = listOf(FabricLaunchConfigurator(storageManager, loaderRepository))
        )
    }

    val runtimeRepository: RuntimeRepository by lazy {
        val client = HttpClient()
        val storage = storageManager
        val downloader = Downloader(client, throughput = throughputTracker, logs = launcherLogRepository)
        val extractor = ArchiveExtractor()
        DefaultRuntimeRepository(
            storage = storage,
            installer = RuntimeInstaller(storage, downloader, extractor),
            verifier = RuntimeVerifier(),
            runtimeCache = runtimeCache
        )
    }

    val launcherLogRepository: LauncherLogRepository by lazy {
        LauncherLogRepository(storageManager)
    }

    /** Release-channel update checks (GitHub releases). */
    val updateRepository: UpdateRepository by lazy {
        GithubUpdateRepository(HttpClient())
    }

    /** Packages logs + diagnostics into a shareable ZIP. */
    val crashReportExporter: CrashReportExporter by lazy {
        CrashReportExporter(
            context = this,
            storage = storageManager,
            logs = launcherLogRepository,
            performance = performanceManager,
            runtimeRepository = runtimeRepository,
            versionPreference = versionPreference,
            loaderRepository = loaderRepository,
            nativeRuntimeManager = nativeRuntimeManager
        )
    }

    val nativeRuntimeManager: NativeRuntimeManager by lazy {
        val storage = storageManager
        DefaultNativeRuntimeManager(
            storage = storage,
            libraryManager = NativeLibraryManager(storage),
            extractionService = NativeExtractionService(storage),
            verificationService = NativeVerificationService(storage),
            rendererPreference = RendererPreference(this),
            detectedArchitecture = NativeArchitecture.detect()
        )
    }

    val launchValidator: LaunchValidator by lazy {
        LaunchValidator(
            storage = storageManager,
            runtimeVerifier = RuntimeVerifier(),
            nativeRuntimeManager = nativeRuntimeManager,
            smartVerifier = performanceManager.verifier(),
            runtimeCache = runtimeCache,
            loaderConfigurator = loaderLaunchConfigurator
        )
    }

    val launchPipeline: LaunchPipeline by lazy {
        val storage = storageManager
        val downloader = Downloader(HttpClient(), throughput = throughputTracker, logs = launcherLogRepository)
        DefaultLaunchPipeline(
            environment = LaunchEnvironment(storage),
            validator = launchValidator,
            classpathBuilder = ClasspathBuilder(storage, performanceManager.cache()),
            argumentBuilder = LaunchArgumentBuilder(storage),
            clientJarManager = ClientJarManager(storage, downloader),
            nativeRuntimeManager = nativeRuntimeManager,
            launcher = NativeJvmLauncher(launcherLogRepository),
            crashAnalyzer = CrashAnalyzer(),
            logs = launcherLogRepository,
            performance = performanceManager,
            loader = loaderLaunchConfigurator,
            inputConfiguration = { inputManager.configuration() },
            surfaceGate = gameSurfaceGate,
            // Rendering is only possible when the PojavLauncher bridge native
            // loaded. Without it the game JVM would boot and then abort the
            // whole app at LWJGL init, so the pipeline fails the launch up
            // front with a clear message instead. See GameSurfaceGate.
            renderBridgeAvailable = { JREUtils.ensureLoaded() == null }
        )
    }

    val inputRepository: InputRepository by lazy {
        JsonInputRepository(storageManager, InputPreferences(this))
    }

    /**
     * Coordinates the "surface before LWJGL init" ordering: [GameActivity]
     * publishes its render surface here and the launch pipeline waits for it
     * before starting the in-process game JVM.
     */
    val gameSurfaceGate: GameSurfaceGate by lazy {
        GameSurfaceGate()
    }

    /**
     * The input framework (profiles, touch pipeline, virtual mouse,
     * keyboard, controller). Initialized in [onCreate]; injectable
     * into any future consumer through this single instance.
     */
    val inputManager: InputManager by lazy {
        DefaultInputManager(
            repository = inputRepository,
            touchMapper = AndroidTouchEventMapper(),
            virtualMouse = DefaultVirtualMouseManager(),
            keyboard = AndroidKeyboardManager(),
            controller = AndroidControllerManager(this),
            logs = launcherLogRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            runCatching { inputManager.initialize() }
            // Unpack the bundled Pojav LWJGL jars so the classpath builder can
            // prepend them; a no-op when the natives haven't been vendored.
            runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    PojavLwjglInstaller(this@LumoCraftApplication, storageManager).install()
                }
            }
            runCatching { defaultVersionRepository.recoverInterruptedInstalls() }
                .onFailure { e ->
                    runCatching {
                        launcherLogRepository.writeLine(
                            "[${recoveryTimestamp()}] [${Thread.currentThread().name}] " +
                                "RECOVERY failed exception=${e.javaClass.name} message=${e.message}"
                        )
                    }
                }
        }

        // Capture uncaught crashes into the launcher log area for later
        // export, then hand off to the platform handler as usual.
        Thread.setDefaultUncaughtExceptionHandler(
            CrashLogHandler(storageManager, Thread.getDefaultUncaughtExceptionHandler())
        )
    }

    private fun recoveryTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    /**
     * The launch request carried from the Home screen into the Launch
     * screen, set just before navigation; cleared once consumed.
     */
    var pendingLaunchContext: LaunchContext? = null
}