package com.arcimage.app.data.archive

import android.content.Context
import android.net.Uri
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.ArchiveEntryItem
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.ArchiveInvalidPasswordException
import com.arcimage.app.model.ArchiveOperationProgress
import com.arcimage.app.model.ArchiveOperationType
import com.arcimage.app.model.ArchivePasswordRequiredException
import com.arcimage.app.model.CompressionConfig
import com.arcimage.app.model.CompressionLevel
import com.arcimage.app.model.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Date
import java.util.UUID

class ArchiveRepositoryImpl(
    private val context: Context
) : ArchiveRepository {

    override suspend fun resolveSourceItems(uris: List<Uri>): List<SourceItem> =
        withContext(Dispatchers.IO) {
            uris.map { uri -> DocumentUriHelper.buildSourceItem(context, uri) }
        }

    override suspend fun inspectArchive(
        archiveUri: Uri,
        password: String?
    ): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val displayName = DocumentUriHelper.resolveDisplayName(context, archiveUri)
        val mimeType = DocumentUriHelper.resolveMimeType(context, archiveUri)
        val format = ArchiveFormat.detectFromFileName(displayName, mimeType)
            ?: throw IllegalArgumentException(displayName)

        when (format) {
            ArchiveFormat.ZIP -> inspectZipArchive(archiveUri, displayName, password)
            ArchiveFormat.SEVEN_Z -> inspectSevenZArchive(archiveUri, displayName, password)
            ArchiveFormat.RAR -> inspectNativeSevenZipArchive(archiveUri, displayName, password)
            ArchiveFormat.TAR,
            ArchiveFormat.TAR_GZ,
            ArchiveFormat.TAR_BZ2,
            ArchiveFormat.TAR_XZ -> inspectTarArchive(archiveUri, format)
            ArchiveFormat.GZ,
            ArchiveFormat.BZ2,
            ArchiveFormat.XZ -> inspectSingleStreamArchive(archiveUri, displayName, format)
        }
    }

    override fun compressItems(
        items: List<SourceItem>,
        config: CompressionConfig,
        destinationTreeUri: Uri?
    ): Flow<ArchiveOperationProgress> = flow {
        val flattened = DocumentUriHelper.flattenSourceItems(context, items)
        val fileEntries = flattened.filter { !it.isDirectory }

        if (config.format.singleFileOnly && (flattened.size != 1 || flattened.first().isDirectory)) {
            throw SingleFileCodecRequiredException()
        }

        val totalBytes = fileEntries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val cleanBaseName = config.archiveBaseName
            .trim()
            .removeSuffix(".${config.format.extension}")
            .ifBlank { "ArcImage_Archive" }
        val targetFileName = "$cleanBaseName.${config.format.extension}"

        val workDir = File(context.cacheDir, "compress_work").apply { mkdirs() }
        val tempArchiveFile = File(workDir, "out_${UUID.randomUUID()}.${config.format.extension}")

        try {
            emit(
                ArchiveOperationProgress(
                    operationType = ArchiveOperationType.COMPRESSING,
                    currentItemName = targetFileName,
                    processedBytes = 0L,
                    totalBytes = totalBytes,
                    completedItems = 0,
                    totalItems = flattened.size
                )
            )

            when (config.format) {
                ArchiveFormat.ZIP -> {
                    compressToZip(
                        entries = flattened,
                        outputFile = tempArchiveFile,
                        config = config,
                        totalBytes = totalBytes,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.SEVEN_Z -> {
                    compressToSevenZ(
                        entries = flattened,
                        outputFile = tempArchiveFile,
                        level = config.level,
                        totalBytes = totalBytes,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZ,
                ArchiveFormat.TAR_BZ2,
                ArchiveFormat.TAR_XZ -> {
                    compressToTarVariant(
                        entries = flattened,
                        outputFile = tempArchiveFile,
                        format = config.format,
                        level = config.level,
                        totalBytes = totalBytes,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.GZ,
                ArchiveFormat.BZ2,
                ArchiveFormat.XZ -> {
                    val singleEntry = fileEntries.first()
                    compressSingleStream(
                        entry = singleEntry,
                        outputFile = tempArchiveFile,
                        format = config.format,
                        level = config.level,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.RAR -> {
                    throw UnsupportedOperationException("RAR is an extraction-only proprietary format")
                }
            }

            currentCoroutineContext().ensureActive()

            val (exportedUri, exportedName) = DocumentUriHelper.exportFileToDestination(
                context = context,
                localSourceFile = tempArchiveFile,
                desiredFileName = targetFileName,
                mimeType = config.format.mimeType,
                destinationTreeUri = destinationTreeUri
            )

            emit(
                ArchiveOperationProgress(
                    operationType = ArchiveOperationType.COMPRESSING,
                    currentItemName = exportedName,
                    processedBytes = totalBytes,
                    totalBytes = totalBytes,
                    completedItems = flattened.size,
                    totalItems = flattened.size,
                    outputUri = exportedUri,
                    outputDisplayName = exportedName
                )
            )
        } finally {
            if (tempArchiveFile.exists()) {
                tempArchiveFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun extractArchive(
        archiveUri: Uri,
        destinationTreeUri: Uri?,
        selectedEntryPaths: Set<String>?,
        password: String?
    ): Flow<ArchiveOperationProgress> = flow {
        val displayName = DocumentUriHelper.resolveDisplayName(context, archiveUri)
        val mimeType = DocumentUriHelper.resolveMimeType(context, archiveUri)
        val format = ArchiveFormat.detectFromFileName(displayName, mimeType)
            ?: throw IllegalArgumentException(displayName)

        val folderStem = stripArchiveExtension(displayName).ifBlank { "Extracted_Archive" }
        val isSafDestination = destinationTreeUri != null && destinationTreeUri.scheme != "file"
        val localExtractRoot = when {
            destinationTreeUri != null && destinationTreeUri.scheme == "file" -> {
                val baseDir = File(destinationTreeUri.path ?: "").takeIf {
                    (it.isDirectory || it.mkdirs()) && it.canWrite()
                } ?: DocumentUriHelper.getDefaultExportsDir(context)
                File(baseDir, folderStem).apply { mkdirs() }
            }

            isSafDestination -> {
                File(context.cacheDir, "extract_${UUID.randomUUID()}/$folderStem").apply { mkdirs() }
            }

            else -> {
                File(DocumentUriHelper.getDefaultExportsDir(context), folderStem).apply { mkdirs() }
            }
        }

        try {
            var extractedCount = 0
            when (format) {
                ArchiveFormat.ZIP -> {
                    extractedCount = extractZipArchive(
                        archiveUri = archiveUri,
                        displayName = displayName,
                        destRoot = localExtractRoot,
                        selectedPaths = selectedEntryPaths,
                        password = password,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.SEVEN_Z -> {
                    extractedCount = extractSevenZArchive(
                        archiveUri = archiveUri,
                        displayName = displayName,
                        destRoot = localExtractRoot,
                        selectedPaths = selectedEntryPaths,
                        password = password,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.RAR -> {
                    extractedCount = extractNativeSevenZipArchive(
                        archiveUri = archiveUri,
                        displayName = displayName,
                        destRoot = localExtractRoot,
                        selectedPaths = selectedEntryPaths,
                        password = password,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZ,
                ArchiveFormat.TAR_BZ2,
                ArchiveFormat.TAR_XZ -> {
                    extractedCount = extractTarArchive(
                        archiveUri = archiveUri,
                        format = format,
                        destRoot = localExtractRoot,
                        selectedPaths = selectedEntryPaths,
                        onProgress = { emit(it) }
                    )
                }
                ArchiveFormat.GZ,
                ArchiveFormat.BZ2,
                ArchiveFormat.XZ -> {
                    extractedCount = extractSingleStreamArchive(
                        archiveUri = archiveUri,
                        displayName = displayName,
                        format = format,
                        destRoot = localExtractRoot,
                        onProgress = { emit(it) }
                    )
                }
            }

            currentCoroutineContext().ensureActive()

            val finalDestinationLabel = if (isSafDestination && destinationTreeUri != null) {
                DocumentUriHelper.copyDirectoryToSafTree(context, localExtractRoot, destinationTreeUri)
            } else {
                localExtractRoot.name
            }

            emit(
                ArchiveOperationProgress(
                    operationType = ArchiveOperationType.EXTRACTING,
                    currentItemName = finalDestinationLabel,
                    completedItems = extractedCount.coerceAtLeast(1),
                    totalItems = extractedCount.coerceAtLeast(1),
                    processedBytes = 100L,
                    totalBytes = 100L,
                    outputDisplayName = finalDestinationLabel
                )
            )
        } finally {
            if (isSafDestination) {
                localExtractRoot.parentFile?.deleteRecursively()
            }
        }
    }.flowOn(Dispatchers.IO)

    // =========================================================================
    // ZIP (Standard Deflate/Store + AES-256 Password Protection via Zip4j)
    // =========================================================================

    private fun inspectZipArchive(
        archiveUri: Uri,
        displayName: String,
        password: String?
    ): List<ArchiveEntryItem> {
        val staged = DocumentUriHelper.copyUriToTempFile(context, archiveUri, "zip")
        val shouldDelete = archiveUri.scheme != "file"
        try {
            val zipFile = if (!password.isNullOrEmpty()) {
                ZipFile(staged, password.toCharArray())
            } else {
                ZipFile(staged)
            }
            val headers = zipFile.fileHeaders
            return headers.mapIndexed { index, header ->
                val rawPath = header.fileName.trimEnd('/')
                ArchiveEntryItem(
                    path = header.fileName,
                    name = rawPath.substringAfterLast('/'),
                    isDirectory = header.isDirectory,
                    uncompressedSize = header.uncompressedSize.coerceAtLeast(0L),
                    compressedSize = header.compressedSize.coerceAtLeast(0L),
                    lastModifiedMillis = header.lastModifiedTimeEpoch,
                    isEncrypted = header.isEncrypted,
                    entryIndex = index
                )
            }
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) {
                throw ArchiveInvalidPasswordException()
            }
            throw e
        } finally {
            if (shouldDelete && staged.exists()) staged.delete()
        }
    }

    private suspend fun compressToZip(
        entries: List<com.arcimage.app.data.util.ResolvedInputEntry>,
        outputFile: File,
        config: CompressionConfig,
        totalBytes: Long,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ) {
        val hasPassword = config.password.isNotEmpty()
        val zipFile = if (hasPassword) {
            ZipFile(outputFile, config.password.toCharArray())
        } else {
            ZipFile(outputFile)
        }

        var processedBytes = 0L
        entries.forEachIndexed { idx, entry ->
            currentCoroutineContext().ensureActive()

            val params = ZipParameters().apply {
                fileNameInZip = entry.relativeArchivePath
                compressionMethod = if (config.level == CompressionLevel.STORE) {
                    CompressionMethod.STORE
                } else {
                    CompressionMethod.DEFLATE
                }
                compressionLevel = when (config.level) {
                    CompressionLevel.STORE -> net.lingala.zip4j.model.enums.CompressionLevel.NO_COMPRESSION
                    CompressionLevel.FAST -> net.lingala.zip4j.model.enums.CompressionLevel.FASTEST
                    CompressionLevel.NORMAL -> net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                    CompressionLevel.MAXIMUM -> net.lingala.zip4j.model.enums.CompressionLevel.MAXIMUM
                }
                if (hasPassword && !entry.isDirectory) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }

            if (!entry.isDirectory) {
                entry.openStream()?.use { rawStream ->
                    zipFile.addStream(rawStream, params)
                }
                processedBytes += entry.sizeBytes.coerceAtLeast(0L)
            }

            onProgress(
                ArchiveOperationProgress(
                    operationType = ArchiveOperationType.COMPRESSING,
                    currentItemName = entry.relativeArchivePath,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    completedItems = idx + 1,
                    totalItems = entries.size
                )
            )
        }
        zipFile.close()
    }

    private suspend fun extractZipArchive(
        archiveUri: Uri,
        displayName: String,
        destRoot: File,
        selectedPaths: Set<String>?,
        password: String?,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ): Int {
        val staged = DocumentUriHelper.copyUriToTempFile(context, archiveUri, "zip")
        val shouldDelete = archiveUri.scheme != "file"
        try {
            val zipFile = ZipFile(staged)
            if (zipFile.isEncrypted) {
                if (password.isNullOrEmpty()) {
                    throw ArchivePasswordRequiredException(displayName)
                }
                zipFile.setPassword(password.toCharArray())
            }

            val targetHeaders = zipFile.fileHeaders.filter { header ->
                selectedPaths.isNullOrEmpty() || matchesSelectedPath(header.fileName, selectedPaths)
            }

            if (targetHeaders.any { it.isEncrypted } && password.isNullOrEmpty()) {
                throw ArchivePasswordRequiredException(displayName)
            }

            val totalBytes = targetHeaders.sumOf { it.uncompressedSize.coerceAtLeast(0L) }
            var processedBytes = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            targetHeaders.forEachIndexed { idx, header ->
                currentCoroutineContext().ensureActive()
                val outTarget = resolveSafeChildFile(destRoot, header.fileName)
                if (header.isDirectory) {
                    outTarget.mkdirs()
                } else {
                    outTarget.parentFile?.mkdirs()
                    try {
                        zipFile.getInputStream(header).use { zis ->
                            FileOutputStream(outTarget).use { fos ->
                                var read = zis.read(buffer)
                                while (read >= 0) {
                                    currentCoroutineContext().ensureActive()
                                    if (read > 0) {
                                        fos.write(buffer, 0, read)
                                        processedBytes += read
                                    }
                                    read = zis.read(buffer)
                                }
                            }
                        }
                    } catch (e: ZipException) {
                        if (e.type == ZipException.Type.WRONG_PASSWORD ||
                            e.message?.contains("Wrong password", ignoreCase = true) == true
                        ) {
                            throw ArchiveInvalidPasswordException()
                        }
                        throw e
                    }
                }
                onProgress(
                    ArchiveOperationProgress(
                        operationType = ArchiveOperationType.EXTRACTING,
                        currentItemName = header.fileName,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes,
                        completedItems = idx + 1,
                        totalItems = targetHeaders.size
                    )
                )
            }
            zipFile.close()
            return targetHeaders.size
        } finally {
            if (shouldDelete && staged.exists()) staged.delete()
        }
    }

    // =========================================================================
    // 7Z (Apache Commons Compress + Native 7-Zip-JBinding Encrypted Header Fallback)
    // =========================================================================

    private fun inspectSevenZArchive(
        archiveUri: Uri,
        displayName: String,
        password: String?
    ): List<ArchiveEntryItem> {
        val staged = DocumentUriHelper.copyUriToTempFile(context, archiveUri, "7z")
        val shouldDelete = archiveUri.scheme != "file"
        try {
            val builder = SevenZFile.builder().setFile(staged)
            if (!password.isNullOrEmpty()) {
                builder.setPassword(password.toCharArray())
            }
            builder.get().use { sevenZ ->
                return sevenZ.entries.mapIndexed { idx, entry ->
                    val cleaned = entry.name.trimEnd('/')
                    ArchiveEntryItem(
                        path = entry.name,
                        name = cleaned.substringAfterLast('/'),
                        isDirectory = entry.isDirectory,
                        uncompressedSize = entry.size.coerceAtLeast(0L),
                        compressedSize = 0L,
                        lastModifiedMillis = if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else 0L,
                        isEncrypted = !password.isNullOrEmpty(),
                        entryIndex = idx
                    )
                }
            }
        } catch (e: PasswordRequiredException) {
            throw ArchivePasswordRequiredException(displayName)
        } catch (e: IOException) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("encrypted", ignoreCase = true) == true
            ) {
                if (password.isNullOrEmpty()) {
                    throw ArchivePasswordRequiredException(displayName)
                } else {
                    throw ArchiveInvalidPasswordException()
                }
            }
            // Fallback to native 7-Zip-JBinding for solid/encrypted 7z headers
            return inspectNativeSevenZipFile(staged, displayName, password)
        } finally {
            if (shouldDelete && staged.exists()) staged.delete()
        }
    }

    private suspend fun compressToSevenZ(
        entries: List<com.arcimage.app.data.util.ResolvedInputEntry>,
        outputFile: File,
        level: CompressionLevel,
        totalBytes: Long,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ) {
        SevenZOutputFile(outputFile).use { sevenZOut ->
            val method = if (level == CompressionLevel.STORE) {
                SevenZMethod.COPY
            } else {
                SevenZMethod.LZMA2
            }
            sevenZOut.setContentCompression(method)

            var processedBytes = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            entries.forEachIndexed { idx, item ->
                currentCoroutineContext().ensureActive()
                val archiveEntry = SevenZArchiveEntry().apply {
                    name = item.relativeArchivePath
                    isDirectory = item.isDirectory
                    lastModifiedDate = Date()
                }
                sevenZOut.putArchiveEntry(archiveEntry)
                if (!item.isDirectory) {
                    item.openStream()?.use { input ->
                        var read = input.read(buffer)
                        while (read > 0) {
                            currentCoroutineContext().ensureActive()
                            sevenZOut.write(buffer, 0, read)
                            processedBytes += read
                            read = input.read(buffer)
                        }
                    }
                }
                sevenZOut.closeArchiveEntry()

                onProgress(
                    ArchiveOperationProgress(
                        operationType = ArchiveOperationType.COMPRESSING,
                        currentItemName = item.relativeArchivePath,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes,
                        completedItems = idx + 1,
                        totalItems = entries.size
                    )
                )
            }
            sevenZOut.finish()
        }
    }

    private suspend fun extractSevenZArchive(
        archiveUri: Uri,
        displayName: String,
        destRoot: File,
        selectedPaths: Set<String>?,
        password: String?,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ): Int {
        val staged = DocumentUriHelper.copyUriToTempFile(context, archiveUri, "7z")
        val shouldDelete = archiveUri.scheme != "file"
        try {
            val builder = SevenZFile.builder().setFile(staged)
            if (!password.isNullOrEmpty()) {
                builder.setPassword(password.toCharArray())
            }
            builder.get().use { sevenZ ->
                val allEntries = sevenZ.entries.toList()
                val totalBytes = allEntries
                    .filter { selectedPaths.isNullOrEmpty() || matchesSelectedPath(it.name, selectedPaths) }
                    .sumOf { it.size.coerceAtLeast(0L) }

                var processedBytes = 0L
                var extractedItems = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                var entry = sevenZ.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    val shouldExtract = selectedPaths.isNullOrEmpty() || matchesSelectedPath(entry.name, selectedPaths)
                    if (shouldExtract) {
                        val outTarget = resolveSafeChildFile(destRoot, entry.name)
                        if (entry.isDirectory) {
                            outTarget.mkdirs()
                        } else {
                            outTarget.parentFile?.mkdirs()
                            FileOutputStream(outTarget).use { fos ->
                                var read = sevenZ.read(buffer)
                                while (read > 0) {
                                    currentCoroutineContext().ensureActive()
                                    fos.write(buffer, 0, read)
                                    processedBytes += read
                                    read = sevenZ.read(buffer)
                                }
                            }
                        }
                        extractedItems++
                        onProgress(
                            ArchiveOperationProgress(
                                operationType = ArchiveOperationType.EXTRACTING,
                                currentItemName = entry.name,
                                processedBytes = processedBytes,
                                totalBytes = totalBytes,
                                completedItems = extractedItems,
                                totalItems = allEntries.size.coerceAtLeast(extractedItems)
                            )
                        )
                    }
                    entry = sevenZ.nextEntry
                }
                return extractedItems
            }
        } catch (e: PasswordRequiredException) {
            // Commons Compress may require external crypto or hit encrypted 7z headers; delegate to native 7-Zip-JBinding
            return extractNativeSevenZipFile(staged, displayName, destRoot, selectedPaths, password, onProgress)
        } catch (e: IOException) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("encrypted", ignoreCase = true) == true ||
                e.message?.contains("Decrypt", ignoreCase = true) == true
            ) {
                return runCatching {
                    extractNativeSevenZipFile(staged, displayName, destRoot, selectedPaths, password, onProgress)
                }.getOrElse { nativeErr ->
                    when (nativeErr) {
                        is ArchivePasswordRequiredException, is ArchiveInvalidPasswordException -> throw nativeErr
                        else -> if (password.isNullOrEmpty()) {
                            throw ArchivePasswordRequiredException(displayName)
                        } else {
                            throw ArchiveInvalidPasswordException()
                        }
                    }
                }
            }
            // Fallback to native 7-Zip-JBinding for BCJ2, PPMd, LZMA, or solid encrypted 7z headers
            return extractNativeSevenZipFile(staged, displayName, destRoot, selectedPaths, password, onProgress)
        } finally {
            if (shouldDelete && staged.exists()) staged.delete()
        }
    }

    // =========================================================================
    // RAR4, RAR5 & Password-Protected RAR/7z via 7-Zip-JBinding-4Android (ARM64 + x86_64)
    // =========================================================================

    private fun inspectNativeSevenZipArchive(
        archiveUri: Uri,
        displayName: String,
        password: String?
    ): List<ArchiveEntryItem> {
        val staged = DocumentUriHelper.copyUriToTempFile(context, archiveUri, "rar")
        val shouldDelete = archiveUri.scheme != "file"
        try {
            return inspectNativeSevenZipFile(staged, displayName, password)
        } finally {
            if (shouldDelete && staged.exists()) staged.delete()
        }
    }

    private fun inspectNativeSevenZipFile(
        file: File,
        displayName: String,
        password: String?
    ): List<ArchiveEntryItem> {
        val openCallback = OpenCryptoCallback(displayName, password)
        RandomAccessFile(file, "r").use { raf ->
            try {
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), openCallback).use { inArchive ->
                    val count = inArchive.numberOfItems
                    val list = ArrayList<ArchiveEntryItem>(count)
                    for (i in 0 until count) {
                        val rawPath = (inArchive.getProperty(i, PropID.PATH) as? String)
                            ?.replace('\\', '/')
                            ?: "item_$i"
                        val isFolder = (inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean) ?: false
                        val size = (inArchive.getProperty(i, PropID.SIZE) as? Number)?.toLong() ?: 0L
                        val packed = (inArchive.getProperty(i, PropID.PACKED_SIZE) as? Number)?.toLong() ?: 0L
                        val encrypted = (inArchive.getProperty(i, PropID.ENCRYPTED) as? Boolean)
                            ?: openCallback.passwordWasRequested
                        val modDate = (inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date)?.time ?: 0L

                        list += ArchiveEntryItem(
                            path = rawPath,
                            name = rawPath.trimEnd('/').substringAfterLast('/'),
                            isDirectory = isFolder,
                            uncompressedSize = size.coerceAtLeast(0L),
                            compressedSize = packed.coerceAtLeast(0L),
                            lastModifiedMillis = modDate,
                            isEncrypted = encrypted,
                            entryIndex = i
                        )
                    }
                    return list
                }
            } catch (e: SevenZipException) {
                openCallback.pendingAuthException?.let { throw it }
                if (openCallback.passwordWasRequested) {
                    if (password.isNullOrEmpty()) {
                        throw ArchivePasswordRequiredException(displayName)
                    } else {
                        throw ArchiveInvalidPasswordException()
                    }
                }
                throw e
            }
        }
    }

    private suspend fun extractNativeSevenZipArchive(
        archiveUri: Uri,
        displayName: String,
        destRoot: File,
        selectedPaths: Set<String>?,
        password: String?,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ): Int {
        val staged = DocumentUriHelper.copyUriToTempFile(context, archiveUri, "rar")
        val shouldDelete = archiveUri.scheme != "file"
        try {
            return extractNativeSevenZipFile(staged, displayName, destRoot, selectedPaths, password, onProgress)
        } finally {
            if (shouldDelete && staged.exists()) staged.delete()
        }
    }

    private suspend fun extractNativeSevenZipFile(
        staged: File,
        displayName: String,
        destRoot: File,
        selectedPaths: Set<String>?,
        password: String?,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ): Int {
        val openCallback = OpenCryptoCallback(displayName, password)
        RandomAccessFile(staged, "r").use { raf ->
            try {
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), openCallback).use { inArchive ->
                    val count = inArchive.numberOfItems
                    val indicesToExtract = mutableListOf<Int>()

                    for (i in 0 until count) {
                        val rawPath = (inArchive.getProperty(i, PropID.PATH) as? String)
                            ?.replace('\\', '/')
                            ?: "item_$i"
                        val encrypted = (inArchive.getProperty(i, PropID.ENCRYPTED) as? Boolean) ?: false
                        if (selectedPaths.isNullOrEmpty() || matchesSelectedPath(rawPath, selectedPaths)) {
                            if (encrypted && password.isNullOrEmpty()) {
                                throw ArchivePasswordRequiredException(displayName)
                            }
                            indicesToExtract += i
                        }
                    }

                    if (indicesToExtract.isEmpty()) return 0

                    onProgress(
                        ArchiveOperationProgress(
                            operationType = ArchiveOperationType.EXTRACTING,
                            currentItemName = displayName,
                            completedItems = 0,
                            totalItems = indicesToExtract.size
                        )
                    )

                    val extractCallback = ExtractCryptoCallback(
                        inArchive = inArchive,
                        destRoot = destRoot,
                        archiveDisplayName = displayName,
                        password = password
                    )

                    inArchive.extract(indicesToExtract.toIntArray(), false, extractCallback)
                    extractCallback.pendingException?.let { throw it }

                    onProgress(
                        ArchiveOperationProgress(
                            operationType = ArchiveOperationType.EXTRACTING,
                            currentItemName = displayName,
                            processedBytes = extractCallback.totalBytes.coerceAtLeast(1L),
                            totalBytes = extractCallback.totalBytes.coerceAtLeast(1L),
                            completedItems = indicesToExtract.size,
                            totalItems = indicesToExtract.size
                        )
                    )
                    return indicesToExtract.size
                }
            } catch (e: SevenZipException) {
                openCallback.pendingAuthException?.let { throw it }
                if (password.isNullOrEmpty() && openCallback.passwordWasRequested) {
                    throw ArchivePasswordRequiredException(displayName)
                }
                if (!password.isNullOrEmpty() &&
                    (e.message?.contains("password", ignoreCase = true) == true || openCallback.passwordWasRequested)
                ) {
                    throw ArchiveInvalidPasswordException()
                }
                throw e
            }
        }
    }

    private class OpenCryptoCallback(
        private val archiveName: String,
        private val password: String?
    ) : IArchiveOpenCallback, ICryptoGetTextPassword {
        var passwordWasRequested: Boolean = false
        var pendingAuthException: Exception? = null

        override fun setTotal(files: Long?, bytes: Long?) = Unit
        override fun setCompleted(files: Long?, bytes: Long?) = Unit

        override fun cryptoGetTextPassword(): String {
            passwordWasRequested = true
            if (password.isNullOrEmpty()) {
                val ex = ArchivePasswordRequiredException(archiveName)
                pendingAuthException = ex
                throw SevenZipException("No password supplied", ex)
            }
            return password
        }
    }

    private inner class ExtractCryptoCallback(
        private val inArchive: net.sf.sevenzipjbinding.IInArchive,
        private val destRoot: File,
        private val archiveDisplayName: String,
        private val password: String?
    ) : IArchiveExtractCallback, ICryptoGetTextPassword {

        var totalBytes: Long = 0L
        var completedBytes: Long = 0L
        var pendingException: Exception? = null
        private var currentOutStream: OutputStream? = null

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            if (extractAskMode != ExtractAskMode.EXTRACT) return null
            val isFolder = (inArchive.getProperty(index, PropID.IS_FOLDER) as? Boolean) ?: false
            val rawPath = (inArchive.getProperty(index, PropID.PATH) as? String)
                ?.replace('\\', '/')
                ?: "item_$index"
            val targetFile = resolveSafeChildFile(destRoot, rawPath)
            if (isFolder) {
                targetFile.mkdirs()
                return null
            }
            targetFile.parentFile?.mkdirs()
            val fos = BufferedOutputStream(FileOutputStream(targetFile))
            currentOutStream = fos
            return ISequentialOutStream { data ->
                fos.write(data)
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            runCatching { currentOutStream?.close() }
            currentOutStream = null
            when (extractOperationResult) {
                ExtractOperationResult.OK -> Unit
                ExtractOperationResult.WRONG_PASSWORD -> {
                    pendingException = if (password.isNullOrEmpty()) {
                        ArchivePasswordRequiredException(archiveDisplayName)
                    } else {
                        ArchiveInvalidPasswordException()
                    }
                }
                ExtractOperationResult.DATAERROR -> {
                    if (password.isNullOrEmpty()) {
                        val isEnc = (0 until inArchive.numberOfItems).any {
                            (inArchive.getProperty(it, PropID.ENCRYPTED) as? Boolean) == true
                        }
                        if (isEnc) {
                            pendingException = ArchivePasswordRequiredException(archiveDisplayName)
                        }
                    } else {
                        pendingException = ArchiveInvalidPasswordException()
                    }
                }
                else -> {
                    pendingException = IOException("Extraction error: $extractOperationResult")
                }
            }
        }

        override fun setTotal(total: Long) {
            totalBytes = total
        }

        override fun setCompleted(complete: Long) {
            completedBytes = complete
        }

        override fun cryptoGetTextPassword(): String {
            if (password.isNullOrEmpty()) {
                val ex = ArchivePasswordRequiredException(archiveDisplayName)
                pendingException = ex
                throw SevenZipException("Password required", ex)
            }
            return password
        }
    }

    // =========================================================================
    // TAR & Stream Codecs (.tar, .tar.gz, .tar.bz2, .tar.xz, .gz, .bz2, .xz)
    // =========================================================================

    private fun inspectTarArchive(
        archiveUri: Uri,
        format: ArchiveFormat
    ): List<ArchiveEntryItem> {
        val rawInput = context.contentResolver.openInputStream(archiveUri)
            ?: FileInputStream(File(archiveUri.path ?: ""))
        wrapDecompressorInput(BufferedInputStream(rawInput), format).use { decompressed ->
            TarArchiveInputStream(decompressed).use { tarIn ->
                val result = mutableListOf<ArchiveEntryItem>()
                var index = 0
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val cleaned = entry.name.trimEnd('/')
                    result += ArchiveEntryItem(
                        path = entry.name,
                        name = cleaned.substringAfterLast('/'),
                        isDirectory = entry.isDirectory,
                        uncompressedSize = entry.realSize.coerceAtLeast(0L),
                        compressedSize = entry.size.coerceAtLeast(0L),
                        lastModifiedMillis = entry.lastModifiedDate.time,
                        isEncrypted = false,
                        entryIndex = index++
                    )
                    entry = tarIn.nextEntry
                }
                return result
            }
        }
    }

    private fun inspectSingleStreamArchive(
        archiveUri: Uri,
        displayName: String,
        format: ArchiveFormat
    ): List<ArchiveEntryItem> {
        val compressedBytes = DocumentUriHelper.resolveFileSize(context, archiveUri)
        val innerName = stripArchiveExtension(displayName).ifBlank { "decompressed_file" }
        return listOf(
            ArchiveEntryItem(
                path = innerName,
                name = innerName,
                isDirectory = false,
                uncompressedSize = 0L,
                compressedSize = compressedBytes,
                lastModifiedMillis = System.currentTimeMillis(),
                isEncrypted = false,
                entryIndex = 0
            )
        )
    }

    private suspend fun compressToTarVariant(
        entries: List<com.arcimage.app.data.util.ResolvedInputEntry>,
        outputFile: File,
        format: ArchiveFormat,
        level: CompressionLevel,
        totalBytes: Long,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ) {
        val fileOut = BufferedOutputStream(FileOutputStream(outputFile))
        val codecOut = wrapCompressorOutput(fileOut, format, level)
        TarArchiveOutputStream(codecOut, "UTF-8").use { tarOut ->
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

            var processedBytes = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            entries.forEachIndexed { idx, entry ->
                currentCoroutineContext().ensureActive()
                val tarEntry = TarArchiveEntry(
                    if (entry.isDirectory && !entry.relativeArchivePath.endsWith("/")) {
                        "${entry.relativeArchivePath}/"
                    } else {
                        entry.relativeArchivePath
                    }
                ).apply {
                    modTime = Date()
                    if (!entry.isDirectory) {
                        size = entry.sizeBytes.coerceAtLeast(0L)
                    }
                }
                tarOut.putArchiveEntry(tarEntry)
                if (!entry.isDirectory) {
                    entry.openStream()?.use { input ->
                        var read = input.read(buffer)
                        while (read > 0) {
                            currentCoroutineContext().ensureActive()
                            tarOut.write(buffer, 0, read)
                            processedBytes += read
                            read = input.read(buffer)
                        }
                    }
                }
                tarOut.closeArchiveEntry()

                onProgress(
                    ArchiveOperationProgress(
                        operationType = ArchiveOperationType.COMPRESSING,
                        currentItemName = entry.relativeArchivePath,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes,
                        completedItems = idx + 1,
                        totalItems = entries.size
                    )
                )
            }
            tarOut.finish()
        }
    }

    private suspend fun compressSingleStream(
        entry: com.arcimage.app.data.util.ResolvedInputEntry,
        outputFile: File,
        format: ArchiveFormat,
        level: CompressionLevel,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var processedBytes = 0L
        wrapCompressorOutput(BufferedOutputStream(FileOutputStream(outputFile)), format, level).use { out ->
            entry.openStream()?.use { input ->
                var read = input.read(buffer)
                while (read > 0) {
                    currentCoroutineContext().ensureActive()
                    out.write(buffer, 0, read)
                    processedBytes += read
                    onProgress(
                        ArchiveOperationProgress(
                            operationType = ArchiveOperationType.COMPRESSING,
                            currentItemName = entry.relativeArchivePath,
                            processedBytes = processedBytes,
                            totalBytes = entry.sizeBytes,
                            completedItems = 0,
                            totalItems = 1
                        )
                    )
                    read = input.read(buffer)
                }
            }
        }
    }

    private suspend fun extractTarArchive(
        archiveUri: Uri,
        format: ArchiveFormat,
        destRoot: File,
        selectedPaths: Set<String>?,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ): Int {
        val rawInput = context.contentResolver.openInputStream(archiveUri)
            ?: FileInputStream(File(archiveUri.path ?: ""))
        wrapDecompressorInput(BufferedInputStream(rawInput), format).use { decompressed ->
            TarArchiveInputStream(decompressed).use { tarIn ->
                var extractedCount = 0
                var processedBytes = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                var entry = tarIn.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    if (selectedPaths.isNullOrEmpty() || matchesSelectedPath(entry.name, selectedPaths)) {
                        val outTarget = resolveSafeChildFile(destRoot, entry.name)
                        if (entry.isDirectory) {
                            outTarget.mkdirs()
                        } else {
                            outTarget.parentFile?.mkdirs()
                            FileOutputStream(outTarget).use { fos ->
                                var read = tarIn.read(buffer)
                                while (read > 0) {
                                    currentCoroutineContext().ensureActive()
                                    fos.write(buffer, 0, read)
                                    processedBytes += read
                                    read = tarIn.read(buffer)
                                }
                            }
                        }
                        extractedCount++
                        onProgress(
                            ArchiveOperationProgress(
                                operationType = ArchiveOperationType.EXTRACTING,
                                currentItemName = entry.name,
                                processedBytes = processedBytes,
                                totalBytes = (processedBytes + 1024L),
                                completedItems = extractedCount,
                                totalItems = extractedCount
                            )
                        )
                    }
                    entry = tarIn.nextEntry
                }
                return extractedCount
            }
        }
    }

    private suspend fun extractSingleStreamArchive(
        archiveUri: Uri,
        displayName: String,
        format: ArchiveFormat,
        destRoot: File,
        onProgress: suspend (ArchiveOperationProgress) -> Unit
    ): Int {
        val innerFileName = stripArchiveExtension(displayName).ifBlank { "extracted_output" }
        val targetFile = resolveSafeChildFile(destRoot, innerFileName)
        targetFile.parentFile?.mkdirs()

        val rawInput = context.contentResolver.openInputStream(archiveUri)
            ?: FileInputStream(File(archiveUri.path ?: ""))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var processedBytes = 0L

        wrapDecompressorInput(BufferedInputStream(rawInput), format).use { input ->
            FileOutputStream(targetFile).use { fos ->
                var read = input.read(buffer)
                while (read > 0) {
                    currentCoroutineContext().ensureActive()
                    fos.write(buffer, 0, read)
                    processedBytes += read
                    onProgress(
                        ArchiveOperationProgress(
                            operationType = ArchiveOperationType.EXTRACTING,
                            currentItemName = innerFileName,
                            processedBytes = processedBytes,
                            totalBytes = processedBytes,
                            completedItems = 0,
                            totalItems = 1
                        )
                    )
                    read = input.read(buffer)
                }
            }
        }
        return 1
    }

    private fun wrapCompressorOutput(
        baseOut: OutputStream,
        format: ArchiveFormat,
        level: CompressionLevel
    ): OutputStream {
        return when (format) {
            ArchiveFormat.TAR -> baseOut
            ArchiveFormat.TAR_GZ, ArchiveFormat.GZ -> {
                val params = GzipParameters().apply {
                    compressionLevel = level.deflateLevel.coerceIn(0, 9)
                }
                GzipCompressorOutputStream(baseOut, params)
            }
            ArchiveFormat.TAR_BZ2, ArchiveFormat.BZ2 -> {
                val blockSize = when (level) {
                    CompressionLevel.STORE, CompressionLevel.FAST -> 1
                    CompressionLevel.NORMAL -> 6
                    CompressionLevel.MAXIMUM -> 9
                }
                BZip2CompressorOutputStream(baseOut, blockSize)
            }
            ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> {
                val preset = when (level) {
                    CompressionLevel.STORE -> 0
                    CompressionLevel.FAST -> 2
                    CompressionLevel.NORMAL -> 6
                    CompressionLevel.MAXIMUM -> 9
                }
                XZCompressorOutputStream(baseOut, preset)
            }
            else -> baseOut
        }
    }

    private fun wrapDecompressorInput(
        baseIn: InputStream,
        format: ArchiveFormat
    ): InputStream {
        return when (format) {
            ArchiveFormat.TAR -> baseIn
            ArchiveFormat.TAR_GZ, ArchiveFormat.GZ -> GzipCompressorInputStream(baseIn, true)
            ArchiveFormat.TAR_BZ2, ArchiveFormat.BZ2 -> BZip2CompressorInputStream(baseIn, true)
            ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZCompressorInputStream(baseIn, true)
            else -> baseIn
        }
    }

    private fun matchesSelectedPath(entryPath: String, selectedPaths: Set<String>): Boolean {
        val normEntry = entryPath.trimEnd('/')
        return selectedPaths.any { sel ->
            val normSel = sel.trimEnd('/')
            normEntry == normSel || normEntry.startsWith("$normSel/")
        }
    }

    private fun resolveSafeChildFile(destRoot: File, entryPath: String): File {
        return ZipSlipGuard.resolveSafeChildFile(destRoot, entryPath)
    }

    private fun stripArchiveExtension(fileName: String): String {
        val lower = fileName.lowercase()
        val compoundSuffixes = listOf(
            ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tbz", ".tar.xz", ".txz"
        )
        for (suffix in compoundSuffixes) {
            if (lower.endsWith(suffix)) {
                return fileName.dropLast(suffix.length)
            }
        }
        return fileName.substringBeforeLast('.', fileName)
    }
}

class SingleFileCodecRequiredException : IllegalArgumentException("Single file codec requires exactly 1 file")
