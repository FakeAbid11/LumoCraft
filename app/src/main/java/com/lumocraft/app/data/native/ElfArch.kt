package com.lumocraft.app.data.native

import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import java.io.RandomAccessFile

/**
 * Minimal ELF header reader used to confirm that an extracted `.so` was
 * actually built for the device's CPU architecture.
 *
 * The old native pipeline trusted the *folder name* a `.so` came from
 * ("natives-linux" → assumed x86_64), so a wrong-ABI binary — e.g. a
 * desktop x86_64 library extracted onto an ARM device — was reported READY
 * and only blew up at `dlopen` with an UnsatisfiedLinkError. Reading the
 * ELF `e_machine` field makes the mismatch detectable up front.
 */
object ElfArch {

    /** ELF `e_machine` values for the architectures we support. */
    private const val EM_386 = 3
    private const val EM_ARM = 40
    private const val EM_X86_64 = 62
    private const val EM_AARCH64 = 183

    /**
     * Reads the ELF `e_machine` of [file], or null when the file is not a
     * readable ELF object (too short, wrong magic, or an I/O error). A null
     * result is treated as "not an ELF we can judge" by callers, never as a
     * mismatch, so non-ELF assets (e.g. `.txt` metadata) are left alone.
     */
    fun machineOf(file: File): Int? {
        if (!file.isFile || file.length() < 20) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val ident = ByteArray(16)
                raf.readFully(ident)
                // ELF magic: 0x7F 'E' 'L' 'F'
                if (ident[0].toInt() != 0x7F || ident[1].toInt() != 'E'.code ||
                    ident[2].toInt() != 'L'.code || ident[3].toInt() != 'F'.code
                ) {
                    return@use null
                }
                // e_type (2 bytes) then e_machine (2 bytes) at offset 18.
                // EI_DATA (ident[5]) selects endianness: 1 = little, 2 = big.
                val littleEndian = ident[5].toInt() != 2
                raf.seek(18)
                val b0 = raf.read()
                val b1 = raf.read()
                if (b0 < 0 || b1 < 0) return@use null
                if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
            }
        }.getOrNull()
    }

    /**
     * True when [file] is an ELF built for [arch]. Non-ELF files return true
     * (nothing to reject); an ELF whose `e_machine` names a different CPU
     * returns false.
     */
    fun matches(file: File, arch: RuntimeArchitecture): Boolean {
        val machine = machineOf(file) ?: return true
        return when (arch) {
            RuntimeArchitecture.ARM64_V8A -> machine == EM_AARCH64
            RuntimeArchitecture.ARMEABI_V7A -> machine == EM_ARM
            RuntimeArchitecture.X86_64 -> machine == EM_X86_64 || machine == EM_386
        }
    }
}
