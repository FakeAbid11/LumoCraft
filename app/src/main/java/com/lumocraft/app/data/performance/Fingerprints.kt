package com.lumocraft.app.data.performance

import java.io.File

/**
 * Cheap content fingerprints for cache invalidation. A fingerprint of
 * `size:lastModified` is several orders of magnitude faster than hashing
 * and exact enough for cache gating: any write to the file changes at
 * least one of the two.
 */
object Fingerprints {

    fun of(file: File): String = "${file.length()}:${file.lastModified()}"

    /** Joins several files' fingerprints (e.g. an inheritsFrom chain). */
    fun of(files: List<File>): String =
        files.joinToString("|") { of(it) }
}