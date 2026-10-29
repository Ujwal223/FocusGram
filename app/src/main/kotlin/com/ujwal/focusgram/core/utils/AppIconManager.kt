package com.ujwal.focusgram.core.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ujwal.focusgram.MainActivity
import com.ujwal.focusgram.R

/**
 * Switches launcher icons dynamically via [PackageManager.setComponentEnabledSetting]
 * on manifest `<activity-alias>` entries.
 *
 * Uses [PackageManager.DONT_KILL_APP] to avoid killing the process during component state changes.
 */
object AppIconManager {
    private const val TAG = "AppIconManager"

    enum class IconOption(
        val id: String,
        val aliasSuffix: String,
        val displayName: String,
        val subtitle: String,
        val category: String,
        @DrawableRes val iconRes: Int
    ) {
        DEFAULT(
            id = "default",
            aliasSuffix = "MainActivityDefault",
            displayName = "FocusGram Classic",
            subtitle = "Signature focus gradient on dark",
            category = "Core",
            iconRes = R.mipmap.ic_launcher
        ),
        PITCH_BLACK(
            id = "pitch_black",
            aliasSuffix = "MainActivityPitchBlack",
            displayName = "Pitch Black",
            subtitle = "True OLED stealth black aesthetic",
            category = "Core",
            iconRes = R.mipmap.ic_launcher_pitch_black
        ),
        ALL_WHITE(
            id = "all_white",
            aliasSuffix = "MainActivityAllWhite",
            displayName = "All White",
            subtitle = "Minimalist bright white with clean camera",
            category = "Core",
            iconRes = R.mipmap.ic_launcher_all_white
        ),

        REDESIGN(
            id = "redesign",
            aliasSuffix = "MainActivityRedesign",
            displayName = "Modern Insta",
            subtitle = "Official modern brand redesign",
            category = "Redesigns",
            iconRes = R.mipmap.ic_launcher_redesign
        ),
        ABSTRACT(
            id = "abstract",
            aliasSuffix = "MainActivityAbstract",
            displayName = "Abstract Art",
            subtitle = "Geometric gradient art redesign",
            category = "Redesigns",
            iconRes = R.mipmap.ic_launcher_abstract
        ),
        FOREST(
            id = "forest",
            aliasSuffix = "MainActivityForest",
            displayName = "Forest Nature",
            subtitle = "Earthy organic botanical tones",
            category = "Redesigns",
            iconRes = R.mipmap.ic_launcher_forest
        ),
        GOLDEN(
            id = "golden",
            aliasSuffix = "MainActivityGolden",
            displayName = "Golden Luxury",
            subtitle = "Deep gold emblem on obsidian",
            category = "Redesigns",
            iconRes = R.mipmap.ic_launcher_golden
        ),
        METAL(
            id = "metal",
            aliasSuffix = "MainActivityMetal",
            displayName = "Titanium Metal",
            subtitle = "Brushed industrial steel & chrome",
            category = "Redesigns",
            iconRes = R.mipmap.ic_launcher_metal
        ),
        SCRIBBLE(
            id = "scribble",
            aliasSuffix = "MainActivityScribble",
            displayName = "Scribble Sketch",
            subtitle = "Handcrafted playful illustration",
            category = "Redesigns",
            iconRes = R.mipmap.ic_launcher_scribble
        ),

        POLAROID(
            id = "polaroid",
            aliasSuffix = "MainActivityPolaroid",
            displayName = "Polaroid Camera",
            subtitle = "Iconic 2010 debut with rainbow stripe",
            category = "Originals",
            iconRes = R.mipmap.ic_launcher_polaroid
        ),
        VIEWFINDER(
            id = "viewfinder",
            aliasSuffix = "MainActivityViewfinder",
            displayName = "Viewfinder",
            subtitle = "2011 classic retro leather camera",
            category = "Originals",
            iconRes = R.mipmap.ic_launcher_viewfinder
        ),
        MINIMALIST(
            id = "minimalist",
            aliasSuffix = "MainActivityMinimalist",
            displayName = "Minimalist Colors",
            subtitle = "2016 breakthrough flat sunset gradient",
            category = "Originals",
            iconRes = R.mipmap.ic_launcher_minimalist
        ),
        VIBRANT(
            id = "vibrant",
            aliasSuffix = "MainActivityVibrant",
            displayName = "Vibrant Gradient",
            subtitle = "2022 radiant illuminated brand design",
            category = "Originals",
            iconRes = R.mipmap.ic_launcher_vibrant
        );

        companion object {
            fun fromId(id: String): IconOption {
                return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
            }
        }
    }

    /**
     * Activates the selected launcher alias and deactivates all others.
     * Also updates the running Activity's [ActivityManager.TaskDescription] for the Recents screen.
     */
    fun setAppIcon(context: Context, iconId: String) {
        val targetOption = IconOption.fromId(iconId)
        val packageManager = context.packageManager
        val packageName = context.packageName

        Log.i(TAG, "Switching app icon to: ${targetOption.displayName} (${targetOption.aliasSuffix})")

        fun enableComponent(suffix: String, enable: Boolean) {
            val state = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val names = listOf(
                ComponentName(packageName, "com.ujwal.focusgram.$suffix"),
                ComponentName(packageName, "$packageName.$suffix")
            )
            for (comp in names) {
                try {
                    packageManager.setComponentEnabledSetting(comp, state, PackageManager.DONT_KILL_APP)
                } catch (_: Exception) {}
            }
        }

        enableComponent(targetOption.aliasSuffix, true)

        for (option in IconOption.values()) {
            if (option == targetOption) continue
            enableComponent(option.aliasSuffix, false)
        }

        if (context is Activity) {
            applyTaskDescription(context, iconId)
        }
    }

    /**
     * Updates the Activity's TaskDescription so the Recents / Multitasking overview screen
     * reflects the exact chosen icon and label immediately.
     */
    fun applyTaskDescription(activity: Activity, iconId: String) {
        try {
            val option = IconOption.fromId(iconId)
            val label = activity.getString(R.string.app_name)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val taskDesc = ActivityManager.TaskDescription.Builder()
                    .setLabel(label)
                    .setIcon(option.iconRes)
                    .build()
                activity.setTaskDescription(taskDesc)
            } else {
                val bmp = BitmapFactory.decodeResource(activity.resources, option.iconRes)
                @Suppress("DEPRECATION")
                val taskDesc = ActivityManager.TaskDescription(label, bmp)
                activity.setTaskDescription(taskDesc)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying TaskDescription for Recents screen: ${e.message}")
        }
    }

    /**
     * Pins a custom home screen shortcut with a user-provided or dynamically downloaded bitmap.
     * Uses ShortcutManagerCompat to support pinning on supported Android launchers.
     */
    fun pinCustomShortcut(context: Context, bitmap: Bitmap, label: String = "Instagram"): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Log.w(TAG, "Pin shortcut is not supported by current launcher")
            return false
        }

        return try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }

            val iconCompat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconCompat.createWithAdaptiveBitmap(bitmap)
            } else {
                IconCompat.createWithBitmap(bitmap)
            }

            val shortcutId = "focusgram_icon_${System.currentTimeMillis()}"
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(label)
                .setIcon(iconCompat)
                .setIntent(launchIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pin custom shortcut: ${e.message}", e)
            false
        }
    }

    private val iconCache = mutableMapOf<Int, Bitmap>()

    /**
     * Safely rasterizes any Android drawable (AdaptiveIconDrawable, VectorDrawable, BitmapDrawable)
     * onto an in-memory ARGB_8888 Bitmap for Compose rendering without crashing on adaptive icons.
     */
    fun getIconBitmap(context: Context, @DrawableRes resId: Int): Bitmap? {
        iconCache[resId]?.let { return it }
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            iconCache[resId] = bitmap
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating icon bitmap for resId $resId: ${e.message}", e)
            null
        }
    }
}
