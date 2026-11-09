package com.arcimage.app.data.archive

import java.io.File

/**
 * Security utility preventing Zip-Slip path traversal attacks when extracting
 * untrusted ZIP, 7Z, RAR, or TAR entries onto the filesystem.
 */
object ZipSlipGuard {

    fun resolveSafeChildFile(destRoot: File, entryPath: String): File {
        val rootCanonical = destRoot.canonicalPath + File.separator
        val normalizedEntry = entryPath
            .replace('\\', '/')
            .trimStart('/')
        if (normalizedEntry.split('/').any { it == ".." }) {
            throw SecurityException("Blocked Zip-Slip path traversal entry: $entryPath")
        }
        val candidate = File(destRoot, normalizedEntry)
        val candidateCanonical = candidate.canonicalPath
        if (!candidateCanonical.startsWith(rootCanonical) &&
            candidateCanonical != destRoot.canonicalPath
        ) {
            throw SecurityException("Blocked Zip-Slip path traversal entry: $entryPath")
        }
        return candidate
    }
}
