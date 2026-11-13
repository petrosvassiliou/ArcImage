package com.arcimage.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arcimage.app.MainActivity
import com.arcimage.app.model.AppDestination
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.model.ResizeMode
import com.arcimage.app.model.ThemePreference
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * High-resolution native screenshot generator for documentation (`README.md`).
 * Skipped during standard CI runs unless `-e captureScreenshots true` is passed.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun checkEnabled() {
        val enabled = InstrumentationRegistry.getArguments().getString("captureScreenshots") == "true"
        Assume.assumeTrue("Skipped unless -e captureScreenshots true is specified", enabled)
    }

    @Test
    fun captureDesktopScreenshots() {
        val activity = composeRule.activity
        val fixture = prepareShowcaseWorkspace(activity)
        val outDir = File(activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

        // 1. Desktop / Googlebook OS — Real Device Files Browser (Light Theme, 2560x1600)
        composeRule.runOnUiThread {
            activity.shellViewModel.setUpdateDialogVisible(false)
            activity.shellViewModel.setLanguageDialogVisible(false)
            activity.shellViewModel.setThemePreference(ThemePreference.LIGHT)
            activity.shellViewModel.selectDestination(AppDestination.FILES)
            activity.fileBrowserViewModel.setGridView(false)
            activity.fileBrowserViewModel.refreshAccess()
            activity.fileBrowserViewModel.openDirectory(fixture.workspaceDir.absolutePath)
        }
        waitForCondition(5_000) {
            activity.fileBrowserViewModel.uiState.value.entries.size >= 6 &&
                !activity.fileBrowserViewModel.uiState.value.isLoading
        }
        composeRule.runOnUiThread {
            activity.fileBrowserViewModel.clearSelection()
            activity.fileBrowserViewModel.toggleSelection(fixture.images[0].absolutePath)
            activity.fileBrowserViewModel.toggleSelection(fixture.images[1].absolutePath)
        }
        settleAndSaveScreenshot(File(outDir, "01-files-browser-desktop.png"), extraDelayMs = 1_100)

        // 2. Desktop / Googlebook OS — Archive Explorer (Main Contents + Bottom-Left Archive Info, Light Theme)
        composeRule.runOnUiThread {
            activity.fileBrowserViewModel.clearSelection()
            activity.shellViewModel.selectDestination(AppDestination.ARCHIVE_MANAGER)
            activity.archiveViewModel.setDestinationTree(
                Uri.fromFile(fixture.workspaceDir),
                fixture.workspaceDir.name
            )
            activity.archiveViewModel.openArchiveForInspection(
                Uri.fromFile(fixture.sampleZip),
                password = "DemoPass!2026"
            )
        }
        waitForCondition(6_000) {
            activity.archiveViewModel.uiState.value.archiveEntries.size >= 5 &&
                activity.archiveViewModel.uiState.value.activeProgress == null
        }
        composeRule.runOnUiThread {
            activity.archiveViewModel.toggleEntrySelection("docs/Architecture_Overview.pdf")
            activity.archiveViewModel.toggleEntrySelection("assets/Hero_Banner_4K.webp")
            activity.archiveViewModel.focusArchiveEntry("docs/Architecture_Overview.pdf")
        }
        settleAndSaveScreenshot(File(outDir, "02-archive-explorer-desktop.png"), extraDelayMs = 900)

        // 3. Desktop / Googlebook OS — Compression Settings Dialog (Destination, Name, Archive Type, Dark Theme)
        composeRule.runOnUiThread {
            activity.shellViewModel.setThemePreference(ThemePreference.DARK)
            activity.shellViewModel.selectDestination(AppDestination.ARCHIVE_MANAGER)
            activity.archiveViewModel.prepareCompressionFromFiles(
                listOf(
                    Uri.fromFile(fixture.designDir),
                    Uri.fromFile(fixture.images[0]),
                    Uri.fromFile(fixture.roadmapPdf)
                )
            )
        }
        waitForCondition(5_000) {
            activity.archiveViewModel.uiState.value.showCompressionDialog &&
                activity.archiveViewModel.uiState.value.sourceItems.size >= 3
        }
        composeRule.runOnUiThread {
            val currentCfg = activity.archiveViewModel.uiState.value.compressionConfig
            activity.archiveViewModel.updateCompressionConfig(
                currentCfg.copy(archiveBaseName = "Q4_Release_Bundle")
            )
        }
        settleAndSaveScreenshot(File(outDir, "03-compression-dialog-dark.png"), extraDelayMs = 900)

        // 4. Desktop / Googlebook OS — Batch Image Studio Master-Detail (Dark Theme, 2560x1600)
        composeRule.runOnUiThread {
            activity.archiveViewModel.setCompressionDialogVisible(false)
            activity.shellViewModel.setThemePreference(ThemePreference.DARK)
            activity.shellViewModel.selectDestination(AppDestination.IMAGE_STUDIO)
            activity.imageStudioViewModel.clearBatch()
            activity.imageStudioViewModel.setDestinationTree(
                Uri.fromFile(fixture.workspaceDir),
                "Studio_Exports"
            )
            activity.imageStudioViewModel.addImages(fixture.images.map { Uri.fromFile(it) })
        }
        waitForCondition(6_000) {
            activity.imageStudioViewModel.uiState.value.batchItems.size == fixture.images.size
        }
        composeRule.runOnUiThread {
            activity.imageStudioViewModel.setResizeMode(ResizeMode.PERCENT_50)
            activity.imageStudioViewModel.setOutputFormat(ImageFormat.WEBP)
            activity.imageStudioViewModel.setQuality(82)
            activity.imageStudioViewModel.setPreserveExif(false)
        }
        settleAndSaveScreenshot(File(outDir, "04-image-studio-desktop-dark.png"), extraDelayMs = 1_400)
    }

    @Test
    fun captureMobileScreenshots() {
        val activity = composeRule.activity
        val fixture = prepareShowcaseWorkspace(activity)
        val outDir = File(activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

        // 5. Android Phone Portrait — Grid Files View + 6-Country-Flag Dropdown Menu (Light Theme, 1080x2400)
        composeRule.runOnUiThread {
            activity.shellViewModel.setUpdateDialogVisible(false)
            activity.shellViewModel.setThemePreference(ThemePreference.LIGHT)
            activity.shellViewModel.selectDestination(AppDestination.FILES)
            activity.fileBrowserViewModel.refreshAccess()
            activity.fileBrowserViewModel.setGridView(true)
            activity.fileBrowserViewModel.openDirectory(fixture.workspaceDir.absolutePath)
        }
        waitForCondition(5_000) {
            activity.fileBrowserViewModel.uiState.value.entries.size >= 6 &&
                !activity.fileBrowserViewModel.uiState.value.isLoading
        }
        composeRule.runOnUiThread {
            activity.shellViewModel.setLanguageDialogVisible(true)
        }
        settleAndSaveScreenshot(File(outDir, "05-mobile-files-and-flags.png"), extraDelayMs = 1_300)

        // 6. Android Phone Portrait — Batch Image Studio Grid & Savings Pill (Dark Theme, 1080x2400)
        composeRule.runOnUiThread {
            activity.shellViewModel.setLanguageDialogVisible(false)
            activity.shellViewModel.setThemePreference(ThemePreference.DARK)
            activity.shellViewModel.selectDestination(AppDestination.IMAGE_STUDIO)
            activity.imageStudioViewModel.clearBatch()
            activity.imageStudioViewModel.setDestinationTree(
                Uri.fromFile(fixture.workspaceDir),
                "Studio_Exports"
            )
            activity.imageStudioViewModel.addImages(fixture.images.map { Uri.fromFile(it) })
        }
        waitForCondition(6_000) {
            activity.imageStudioViewModel.uiState.value.batchItems.size == fixture.images.size
        }
        composeRule.runOnUiThread {
            activity.imageStudioViewModel.setResizeMode(ResizeMode.PERCENT_50)
            activity.imageStudioViewModel.setOutputFormat(ImageFormat.WEBP)
            activity.imageStudioViewModel.setQuality(82)
        }
        settleAndSaveScreenshot(File(outDir, "06-mobile-image-studio-dark.png"), extraDelayMs = 1_400)
    }

    private fun waitForCondition(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (predicate()) return
            Thread.sleep(80)
        }
    }

    private fun settleAndSaveScreenshot(targetFile: File, extraDelayMs: Long) {
        composeRule.waitForIdle()
        Thread.sleep(extraDelayMs)
        composeRule.waitForIdle()

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var bitmap: Bitmap? = null
        repeat(6) {
            bitmap = uiAutomation.takeScreenshot()
            if (bitmap != null) return@repeat
            Thread.sleep(250)
        }
        val captured = requireNotNull(bitmap) { "UiAutomation.takeScreenshot() returned null" }
        FileOutputStream(targetFile).use { out ->
            captured.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private data class ShowcaseFixture(
        val workspaceDir: File,
        val designDir: File,
        val sampleZip: File,
        val roadmapPdf: File,
        val images: List<File>
    )

    private fun prepareShowcaseWorkspace(activity: MainActivity): ShowcaseFixture {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseParent = if ((downloadsDir.exists() || downloadsDir.mkdirs()) && downloadsDir.canWrite()) {
            downloadsDir
        } else {
            activity.externalCacheDir ?: activity.cacheDir
        }

        val workspaceDir = File(baseParent, "Studio_Workspace").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val designDir = File(workspaceDir, "Design_System").apply { mkdirs() }
        listOf("Tokens_2026.json", "Typography_Scale.md", "Color_Palette.ase", "Grid_Layout.fig", "Components.kt", "Icons_Pack.svg")
            .forEachIndexed { idx, name ->
                File(designDir, name).writeText("Design asset #$idx — ".repeat(180))
            }

        val cameraDir = File(workspaceDir, "Camera_Originals").apply { mkdirs() }
        repeat(4) { i -> File(cameraDir, "DSC_041$i.dng").writeBytes(ByteArray(24_000) { (it + i).toByte() }) }

        val invoicesDir = File(workspaceDir, "Invoices_2026").apply { mkdirs() }
        repeat(3) { i -> File(invoicesDir, "Invoice_2026_0${i + 1}.pdf").writeBytes(ByteArray(18_500) { (it * 3).toByte() }) }

        val img1 = File(workspaceDir, "Alpine_Sunrise_4K.jpg").also {
            writeSceneBitmap(it, Bitmap.CompressFormat.JPEG, 96, SceneStyle.ALPINE_SUNRISE)
        }
        val img2 = File(workspaceDir, "Aegean_Coastline.png").also {
            writeSceneBitmap(it, Bitmap.CompressFormat.PNG, 100, SceneStyle.AEGEAN_COAST)
        }
        val img3 = File(workspaceDir, "Minimal_Architecture.jpg").also {
            writeSceneBitmap(it, Bitmap.CompressFormat.JPEG, 96, SceneStyle.ARCHITECTURE)
        }
        val img4 = File(workspaceDir, "Botanical_Emerald.png").also {
            writeSceneBitmap(it, Bitmap.CompressFormat.PNG, 100, SceneStyle.BOTANICAL)
        }

        val roadmapPdf = File(workspaceDir, "Product_Roadmap_2026.pdf").apply {
            writeBytes("%PDF-1.7\n".toByteArray() + ByteArray(318_000) { (it % 97 + 32).toByte() })
        }

        // Real multi-entry ZIP with standard compressed files + AES-256 encrypted entry
        val sampleZip = File(workspaceDir, "Client_Deliverables_2026.zip")
        ZipFile(sampleZip).use { zip ->
            val stdParams = { entryPath: String ->
                ZipParameters().apply {
                    fileNameInZip = entryPath
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                }
            }
            zip.addStream(
                ByteArrayInputStream("ArcImage Architecture Overview — ".repeat(5_600).toByteArray()),
                stdParams("docs/Architecture_Overview.pdf")
            )
            zip.addStream(
                ByteArrayInputStream(img2.readBytes()),
                stdParams("assets/Hero_Banner_4K.webp")
            )
            zip.addStream(
                ByteArrayInputStream("Release v2.4 Notes & Changelog\n".repeat(920).toByteArray()),
                stdParams("docs/Release_Notes_v2.md")
            )
            zip.addStream(
                ByteArrayInputStream("package com.arcimage.engine\nclass CodecPipeline {}\n".repeat(1_100).toByteArray()),
                stdParams("src/CodecPipeline.kt")
            )
            zip.addStream(
                ByteArrayInputStream("<svg viewBox='0 0 108 108'></svg>".repeat(480).toByteArray()),
                stdParams("assets/Brand_Mark.svg")
            )
        }
        ZipFile(sampleZip, "DemoPass!2026".toCharArray()).use { encZip ->
            val encParams = ZipParameters().apply {
                fileNameInZip = "vault/Signing_Config.enc"
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.MAXIMUM
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            encZip.addStream(
                ByteArrayInputStream("Encrypted Vault Configuration Payload ".repeat(600).toByteArray()),
                encParams
            )
        }

        // Real 7z archive in the workspace folder
        val sevenZFile = File(workspaceDir, "Studio_RAW_Backup.7z")
        SevenZOutputFile(sevenZFile).use { sevenZ ->
            sevenZ.setContentCompression(SevenZMethod.LZMA2)
            val entry = SevenZArchiveEntry().apply {
                name = "raw/Session_Metadata.xmp"
                isDirectory = false
            }
            sevenZ.putArchiveEntry(entry)
            sevenZ.write("<x:xmpmeta>Camera RAW Session</x:xmpmeta>".repeat(900).toByteArray())
            sevenZ.closeArchiveEntry()
            sevenZ.finish()
        }

        return ShowcaseFixture(
            workspaceDir = workspaceDir,
            designDir = designDir,
            sampleZip = sampleZip,
            roadmapPdf = roadmapPdf,
            images = listOf(img1, img2, img3, img4)
        )
    }

    private enum class SceneStyle {
        ALPINE_SUNRISE,
        AEGEAN_COAST,
        ARCHITECTURE,
        BOTANICAL
    }

    private fun writeSceneBitmap(
        file: File,
        format: Bitmap.CompressFormat,
        quality: Int,
        style: SceneStyle
    ) {
        val w = 1920
        val h = 1200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (style) {
            SceneStyle.ALPINE_SUNRISE -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(
                        Color.parseColor("#1E1B4B"),
                        Color.parseColor("#6D28D9"),
                        Color.parseColor("#F59E0B"),
                        Color.parseColor("#FDE68A")
                    ),
                    floatArrayOf(0f, 0.42f, 0.78f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                // Sun glow
                paint.shader = RadialGradient(
                    w * 0.64f, h * 0.48f, h * 0.34f,
                    intArrayOf(Color.parseColor("#FFFBEB"), Color.parseColor("#88FBBF24"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(w * 0.64f, h * 0.48f, h * 0.34f, paint)
                paint.shader = null
                paint.color = Color.parseColor("#FEF3C7")
                canvas.drawCircle(w * 0.64f, h * 0.48f, h * 0.085f, paint)

                // Layered mountain ridges
                drawRidge(canvas, w, h, Color.parseColor("#4C1D95"), floatArrayOf(0.68f, 0.50f, 0.61f, 0.42f, 0.57f, 0.48f, 0.65f))
                drawRidge(canvas, w, h, Color.parseColor("#312E81"), floatArrayOf(0.76f, 0.58f, 0.70f, 0.52f, 0.68f, 0.60f, 0.74f))
                drawRidge(canvas, w, h, Color.parseColor("#1E1B4B"), floatArrayOf(0.86f, 0.72f, 0.80f, 0.66f, 0.79f, 0.70f, 0.84f))
            }

            SceneStyle.AEGEAN_COAST -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.58f,
                    intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#38BDF8"), Color.parseColor("#BAE6FD")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h * 0.58f, paint)

                paint.shader = LinearGradient(
                    0f, h * 0.56f, 0f, h.toFloat(),
                    intArrayOf(Color.parseColor("#06B6D4"), Color.parseColor("#0369A1"), Color.parseColor("#0F172A")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, h * 0.56f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null

                paint.color = Color.parseColor("#FDE047")
                canvas.drawCircle(w * 0.28f, h * 0.26f, h * 0.09f, paint)

                // Sunlit headland
                drawRidge(canvas, w, h, Color.parseColor("#D97706"), floatArrayOf(0.62f, 0.46f, 0.55f, 0.68f, 0.90f, 1.0f, 1.0f))
                paint.color = Color.parseColor("#55FFFFFF")
                for (i in 0..7) {
                    val y = h * (0.63f + i * 0.04f)
                    canvas.drawRoundRect(w * (0.14f + i * 0.02f), y, w * (0.44f - i * 0.015f), y + 6f, 4f, 4f, paint)
                }
            }

            SceneStyle.ARCHITECTURE -> {
                paint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(Color.parseColor("#0EA5E9"), Color.parseColor("#38BDF8"), Color.parseColor("#E0F2FE")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null

                val wall = Path().apply {
                    moveTo(0f, h * 0.32f)
                    lineTo(w * 0.72f, h * 0.14f)
                    lineTo(w * 0.72f, h.toFloat())
                    lineTo(0f, h.toFloat())
                    close()
                }
                paint.color = Color.parseColor("#EA580C")
                canvas.drawPath(wall, paint)

                val wing = Path().apply {
                    moveTo(w * 0.42f, h * 0.44f)
                    lineTo(w.toFloat(), h * 0.26f)
                    lineTo(w.toFloat(), h.toFloat())
                    lineTo(w * 0.42f, h.toFloat())
                    close()
                }
                paint.color = Color.parseColor("#FDBA74")
                canvas.drawPath(wing, paint)

                paint.color = Color.parseColor("#1E293B")
                for (col in 0..3) {
                    val left = w * (0.50f + col * 0.11f)
                    canvas.drawRoundRect(left, h * 0.48f, left + w * 0.06f, h * 0.86f, 24f, 24f, paint)
                }
            }

            SceneStyle.BOTANICAL -> {
                paint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(Color.parseColor("#022C22"), Color.parseColor("#065F46"), Color.parseColor("#0D9488")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null

                val colors = intArrayOf(
                    Color.parseColor("#10B981"),
                    Color.parseColor("#34D399"),
                    Color.parseColor("#2DD4BF"),
                    Color.parseColor("#A7F3D0")
                )
                for (i in 0..7) {
                    paint.color = colors[i % colors.size]
                    paint.alpha = 185
                    val cx = w * (0.12f + i * 0.12f)
                    val cy = h * (if (i % 2 == 0) 0.72f else 0.48f)
                    canvas.save()
                    canvas.rotate(-28f + i * 9f, cx, cy)
                    canvas.drawOval(cx - 190f, cy - 420f, cx + 190f, cy + 420f, paint)
                    canvas.restore()
                }
            }
        }

        FileOutputStream(file).use { out ->
            bmp.compress(format, quality, out)
        }
        bmp.recycle()
    }

    private fun drawRidge(canvas: Canvas, w: Int, h: Int, color: Int, heights: FloatArray) {
        val path = Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(0f, h * heights[0])
            val step = w.toFloat() / (heights.size - 1)
            for (i in 1 until heights.size) {
                lineTo(step * i, h * heights[i])
            }
            lineTo(w.toFloat(), h.toFloat())
            close()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawPath(path, p)
    }
}
