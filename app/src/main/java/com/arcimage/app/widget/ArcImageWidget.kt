package com.arcimage.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.arcimage.app.MainActivity
import com.arcimage.app.R

class ArcImageWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_TILE = DpSize(180.dp, 110.dp)
        private val DESKTOP_WIDE = DpSize(300.dp, 120.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_TILE, DESKTOP_WIDE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val extractIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_QUICK_EXTRACT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val imageIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_QUICK_IMAGE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            GlanceTheme {
                ArcImageWidgetContent(
                    context = context,
                    extractIntent = extractIntent,
                    imageIntent = imageIntent
                )
            }
        }
    }

    @Composable
    private fun ArcImageWidgetContent(
        context: Context,
        extractIntent: Intent,
        imageIntent: Intent
    ) {
        val widgetSize = LocalSize.current
        val isWideDesktop = widgetSize.width >= 260.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Header Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_arcimage_logo),
                    contentDescription = context.getString(R.string.app_name),
                    modifier = GlanceModifier.size(22.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column {
                    Text(
                        text = context.getString(R.string.app_name),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (isWideDesktop) {
                        Text(
                            text = context.getString(R.string.app_subtitle),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Responsive Quick-Action Buttons (Side-by-side on Googlebook OS Desktop, Stacked on Compact)
            if (isWideDesktop) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickActionTile(
                        iconRes = R.drawable.ic_widget_archive,
                        label = context.getString(R.string.widget_action_extract),
                        intent = extractIntent,
                        isPrimary = true,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    QuickActionTile(
                        iconRes = R.drawable.ic_widget_image,
                        label = context.getString(R.string.widget_action_image),
                        intent = imageIntent,
                        isPrimary = false,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    QuickActionTile(
                        iconRes = R.drawable.ic_widget_archive,
                        label = context.getString(R.string.widget_action_extract),
                        intent = extractIntent,
                        isPrimary = true,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    QuickActionTile(
                        iconRes = R.drawable.ic_widget_image,
                        label = context.getString(R.string.widget_action_image),
                        intent = imageIntent,
                        isPrimary = false,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    @Composable
    private fun QuickActionTile(
        iconRes: Int,
        label: String,
        intent: Intent,
        isPrimary: Boolean,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val bgColor = if (isPrimary) {
            GlanceTheme.colors.primaryContainer
        } else {
            GlanceTheme.colors.secondaryContainer
        }
        val textColor = if (isPrimary) {
            GlanceTheme.colors.onPrimaryContainer
        } else {
            GlanceTheme.colors.onSecondaryContainer
        }

        Box(
            modifier = modifier
                .cornerRadius(16.dp)
                .background(bgColor)
                .clickable(actionStartActivity(intent))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = label,
                    maxLines = 1,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
