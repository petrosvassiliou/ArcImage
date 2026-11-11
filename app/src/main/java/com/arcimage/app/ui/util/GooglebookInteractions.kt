package com.arcimage.app.ui.util

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.DragEvent
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Mouse & haptic trackpad interaction modifier supporting:
 * - Primary click
 * - Secondary mouse / two-finger Googlebook trackpad right-click with exact cursor offset
 * - Touchscreen long-press fallback with haptic feedback
 */
@Composable
fun Modifier.arcPointerContextActions(
    key: Any?,
    onPrimaryClick: () -> Unit,
    onContextMenuRequested: (Offset) -> Unit
): Modifier {
    val haptics = LocalHapticFeedback.current
    return this
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(key) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        val position = event.changes.firstOrNull()?.position ?: Offset.Zero
                        event.changes.forEach { it.consume() }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onContextMenuRequested(position)
                    }
                }
            }
        }
        .pointerInput(key) {
            detectTapGestures(
                onTap = { onPrimaryClick() },
                onLongPress = { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onContextMenuRequested(offset)
                }
            )
        }
}

/**
 * Tracks desktop mouse / trackpad hover state for high-DPI Googlebook OS item cards.
 */
@Composable
fun rememberArcHoverState(): Pair<MutableInteractionSource, Boolean> {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    return interactionSource to isHovered
}

fun Modifier.arcHoverable(interactionSource: MutableInteractionSource): Modifier =
    this.hoverable(interactionSource = interactionSource)

/**
 * Native Googlebook OS & Android 17+ Drag-and-Drop target accepting files, folders,
 * archives, and images dragged from the Googlebook OS File Manager or adjacent windows.
 */
@Composable
fun Modifier.arcFileDropTarget(
    onHoverChanged: (Boolean) -> Unit,
    onUrisDropped: (List<Uri>) -> Unit
): Modifier {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val dropTarget = remember(context, onUrisDropped, onHoverChanged) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                onHoverChanged(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                onHoverChanged(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                onHoverChanged(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onHoverChanged(false)
                val androidDragEvent: DragEvent = event.toAndroidDragEvent()
                val activity = context.findActivity()

                // Request cross-window URI read permissions on Googlebook OS / multi-window Android
                runCatching {
                    activity?.requestDragAndDropPermissions(androidDragEvent)
                }

                val clipData = androidDragEvent.clipData ?: return false
                val collectedUris = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i)?.uri
                    if (uri != null) {
                        collectedUris += uri
                    }
                }
                if (collectedUris.isNotEmpty()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUrisDropped(collectedUris)
                    return true
                }
                return false
            }
        }
    }

    return this.dragAndDropTarget(
        shouldStartDragAndDrop = { startEvent ->
            val types = startEvent.mimeTypes()
            types.isNotEmpty() && !types.contains(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                types.any { it.startsWith("image/") || it.startsWith("application/") || it == "*/*" || it.contains("uri") }
        },
        target = dropTarget
    )
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
