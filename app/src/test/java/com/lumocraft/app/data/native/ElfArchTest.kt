package com.lumocraft.app.data.native

import com.lumocraft.app.domain.runtime.RuntimeArchitecture
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Pure-JVM tests for the ELF architecture reader used by native verification. */
class ElfArchTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Writes a minimal little-endian ELF header with the given e_machine. */
    private fun elf(machine: Int, name: String = "lib.so"): File {
        val bytes = ByteArray(20)
        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2 // ELFCLASS64
        bytes[5] = 1 // ELFDATA2LSB (little endian)
        // e_machine at offset 18, little endian
        bytes[18] = (machine and 0xFF).toByte()
        bytes[19] = ((machine shr 8) and 0xFF).toByte()
        return File(tmp.root, name).apply { writeBytes(bytes) }
    }

    @Test
    fun `aarch64 elf matches arm64 only`() {
        val file = elf(183) // EM_AARCH64
        assertTrue(ElfArch.matches(file, RuntimeArchitecture.ARM64_V8A))
        assertFalse(ElfArch.matches(file, RuntimeArchitecture.X86_64))
        assertFalse(ElfArch.matches(file, RuntimeArchitecture.ARMEABI_V7A))
    }

    @Test
    fun `x86_64 elf matches x86_64 only`() {
        val file = elf(62) // EM_X86_64
        assertTrue(ElfArch.matches(file, RuntimeArchitecture.X86_64))
        assertFalse(ElfArch.matches(file, RuntimeArchitecture.ARM64_V8A))
    }

    @Test
    fun `arm elf matches arm32 only`() {
        val file = elf(40) // EM_ARM
        assertTrue(ElfArch.matches(file, RuntimeArchitecture.ARMEABI_V7A))
        assertFalse(ElfArch.matches(file, RuntimeArchitecture.ARM64_V8A))
    }

    @Test
    fun `non-elf file is not judged and returns match`() {
        val file = File(tmp.root, "notes.txt").apply { writeText("hello world not an elf") }
        assertNull(ElfArch.machineOf(file))
        // Non-ELF files are left alone (nothing to reject).
        assertTrue(ElfArch.matches(file, RuntimeArchitecture.ARM64_V8A))
    }

    @Test
    fun `too-short file returns null machine`() {
        val file = File(tmp.root, "tiny.so").apply { writeBytes(byteArrayOf(0x7F, 'E'.code.toByte())) }
        assertNull(ElfArch.machineOf(file))
    }
}
