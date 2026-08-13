package com.lumocraft.app

import android.app.Application
import com.lumocraft.app.data.account.SharedPreferencesAccountRepository
import com.lumocraft.app.data.network.Downloader
import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.data.runtime.ArchiveExtractor
import com.lumocraft.app.data.runtime.DefaultRuntimeRepository
import com.lumocraft.app.data.runtime.RuntimeInstaller
import com.lumocraft.app.data.runtime.RuntimeVerifier
import com.lumocraft.app.data.storage.StorageManager
import com.lumocraft.app.data.version.AssetInstaller
import com.lumocraft.app.data.version.DefaultVersionRepository
import com.lumocraft.app.data.version.LibraryInstaller
import com.lumocraft.app.data.version.ManifestService
import com.lumocraft.app.data.version.VerificationService
import com.lumocraft.app.data.version.VersionInstaller
import com.lumocraft.app.domain.account.AccountRepository
import com.lumocraft.app.domain.runtime.RuntimeRepository
import com.lumocraft.app.domain.version.VersionRepository

/**
 * Minimal manual dependency container — no DI framework needed.
 * Repositories are created once and shared through the app; ViewModels
 * resolve them via their factories.
 */
class LumoCraftApplication : Application() {

    val accountRepository: AccountRepository by lazy {
        SharedPreferencesAccountRepository(this)
    }

    val versionRepository: VersionRepository by lazy {
        val client = HttpClient()
        val storage = StorageManager(this)
        val downloader = Downloader(client)
        DefaultVersionRepository(
            manifestService = ManifestService(client),
            installer = VersionInstaller(
                storage = storage,
                downloader = downloader,
                libraryInstaller = LibraryInstaller(storage, downloader),
                assetInstaller = AssetInstaller(storage, downloader),
                verificationService = VerificationService(storage)
            ),
            storage = storage
        )
    }

    val runtimeRepository: RuntimeRepository by lazy {
        val client = HttpClient()
        val storage = StorageManager(this)
        val downloader = Downloader(client)
        val extractor = ArchiveExtractor()
        DefaultRuntimeRepository(
            storage = storage,
            installer = RuntimeInstaller(storage, downloader, extractor),
            verifier = RuntimeVerifier()
        )
    }
}