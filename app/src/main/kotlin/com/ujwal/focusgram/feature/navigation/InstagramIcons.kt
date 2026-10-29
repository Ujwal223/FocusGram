package com.ujwal.focusgram.feature.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Vector icon assets for navigation tabs and action controls.
 */
object InstagramIcons {

    private fun parseNodes(pathData: String) = PathParser().parsePathString(pathData).toNodes()

    // 1. Home Outline: akar-icons--home-alt1.svg (24x24)
    val HomeOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("M21 19v-6.733a4 4 0 0 0-1.245-2.9L13.378 3.31a2 2 0 0 0-2.755 0L4.245 9.367A4 4 0 0 0 3 12.267V19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = parseNodes("M9 15a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v6H9z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }

    // 2. Home Filled: fluent--home-16-filled.svg (16x16)
    val HomeFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            addPath(
                pathData = parseNodes("M8.687 1.262a1 1 0 0 0-1.374 0L2.469 5.84A1.5 1.5 0 0 0 2 6.931v5.57A1.5 1.5 0 0 0 3.5 14H5a1.5 1.5 0 0 0 1.5-1.5V10a.5.5 0 0 1 .5-.5h2a.5.5 0 0 1 .5.5v2.5A1.5 1.5 0 0 0 11 14h1.5a1.5 1.5 0 0 0 1.5-1.5V6.93a1.5 1.5 0 0 0-.47-1.09z"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // 3. Search Outline: akar-icons--search.svg (24x24)
    val SearchOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "SearchOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("m21 21l-4.486-4.494M19 10.5a8.5 8.5 0 1 1-17 0a8.5 8.5 0 0 1 17 0Z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            )
        }.build()
    }

    // 4. Search Filled: boxicons--search-filled.svg (24x24)
    val SearchFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "SearchFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("M10 18c1.85 0 3.54-.63 4.9-1.69l5.1 5.1L21.41 20l-5.1-5.1A8 8 0 0 0 18 10c0-4.41-3.59-8-8-8s-8 3.59-8 8s3.59 8 8 8"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // 5. Reels Outline: selfhst--instagram-reels-dark.svg (512x512)
    val ReelsOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ReelsOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 512f,
            viewportHeight = 512f
        ).apply {
            addPath(
                pathData = parseNodes("M148 0h216c40.2 0 76.8 16.5 103.3 43c26.6 26.5 43.1 63.2 43.1 103.4v219.2c0 40.2-16.5 76.8-43 103.3l-.1.1c-26.6 26.5-63.2 43-103.3 43H148c-40.2 0-76.9-16.5-103.4-43l-1-1.1c-25.9-26.4-42-62.6-42-102.3V146.4c0-40.3 16.5-76.9 43-103.4S107.7 0 148 0m234.8 129.2l.3.5h90c-3.6-23.7-14.9-44.9-31.2-61.2c-20-20-47.6-32.4-77.9-32.4h-37zm-41.6.5l-56-93.6h-123l56.7 93.6zm-164.2 0l-54.8-90.6c-20 4.8-38 15.2-52.1 29.4c-16.3 16.3-27.6 37.5-31.2 61.2zm297.4 36.1H37.6v199.8c0 29.9 12 57.1 31.5 77l.9.9c20 20 47.6 32.5 77.9 32.5H364c30.3 0 57.9-12.4 77.9-32.4l.1-.1c20-20 32.4-47.5 32.4-77.9zM213.8 229l111.8 72.7c1.8 1.2 3.4 2.7 4.7 4.5c5.1 7.4 3.2 17.5-4.2 22.6l-111.3 65.7c-2.8 2.3-6.4 3.7-10.3 3.7c-9 0-16.3-7.3-16.3-16.3V242.3h.1c0-3.2 1-6.5 2.9-9.3c5.2-7.4 15.3-9.2 22.6-4"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // 6. Reels Filled: selfhst--instagram-reels-light.svg (512x512)
    val ReelsFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ReelsFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 512f,
            viewportHeight = 512f
        ).apply {
            addPath(
                pathData = parseNodes("M148 0h216c40.2 0 76.8 16.5 103.3 43c26.6 26.5 43.1 63.2 43.1 103.4v219.2c0 40.2-16.5 76.8-43 103.3l-.1.1c-26.6 26.5-63.2 43-103.3 43H148c-40.2 0-76.9-16.5-103.4-43l-1-1.1c-25.9-26.4-42-62.6-42-102.3V146.4c0-40.3 16.5-76.9 43-103.4S107.7 0 148 0m234.8 129.2l.3.5h90c-3.6-23.7-14.9-44.9-31.2-61.2c-20-20-47.6-32.4-77.9-32.4h-37zm-41.6.5l-56-93.6h-123l56.7 93.6zm-164.2 0l-54.8-90.6c-20 4.8-38 15.2-52.1 29.4c-16.3 16.3-27.6 37.5-31.2 61.2zm297.4 36.1H37.6v199.8c0 29.9 12 57.1 31.5 77l.9.9c20 20 47.6 32.5 77.9 32.5H364c30.3 0 57.9-12.4 77.9-32.4l.1-.1c20-20 32.4-47.5 32.4-77.9zM213.8 229l111.8 72.7c1.8 1.2 3.4 2.7 4.7 4.5c5.1 7.4 3.2 17.5-4.2 22.6l-111.3 65.7c-2.8 2.3-6.4 3.7-10.3 3.7c-9 0-16.3-7.3-16.3-16.3V242.3h.1c0-3.2 1-6.5 2.9-9.3c5.2-7.4 15.3-9.2 22.6-4"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // 7. Direct Outline: DM).svg (24x24)
    val DirectOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "DirectOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("m10.308 13.692l4.846-4.846M20.11 5.89l-4.09 13.294c-.367 1.192-.55 1.788-.867 1.985a1 1 0 0 1-.912.076c-.344-.143-.624-.7-1.182-1.816l-2.59-5.182a2 2 0 0 0-.193-.342a1 1 0 0 0-.18-.181a2 2 0 0 0-.331-.186L4.572 10.94c-1.115-.558-1.673-.837-1.816-1.181a1 1 0 0 1 .076-.913c.197-.316.793-.5 1.985-.867l13.295-4.09c.937-.289 1.405-.433 1.722-.316a1 1 0 0 1 .594.594c.116.316-.028.784-.316 1.72z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }

    // 8. Direct Filled: DM (Filled).svg (640x640)
    val DirectFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "DirectFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 640f,
            viewportHeight = 640f
        ).apply {
            addPath(
                pathData = parseNodes("M568.4 37.7c9.8-3.5 20.6-1 28 6.3s9.8 18.2 6.3 28l-178 496.9c-5 13.9-18.1 23.1-32.8 23.1c-14.2 0-27-8.6-32.3-21.7l-64.2-158c-4.5-11-2.5-23.6 5.2-32.6l94.5-112.4c5.1-6.1 4.7-15-.9-20.6s-14.6-6-20.6-.9l-112.4 94.3c-9.1 7.6-21.6 9.6-32.6 5.2L70.1 280.8c-13.1-5.3-21.7-18.1-21.7-32.3c0-14.7 9.2-27.8 23.1-32.8z"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // 9. Profile Outline: ant-design--user-outlined.svg (1024x1024)
    val ProfileOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ProfileOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f
        ).apply {
            addPath(
                pathData = parseNodes("M858.5 763.6a374 374 0 0 0-80.6-119.5a375.6 375.6 0 0 0-119.5-80.6c-.4-.2-.8-.3-1.2-.5C719.5 518 760 444.7 760 362c0-137-111-248-248-248S264 225 264 362c0 82.7 40.5 156 102.8 201.1c-.4.2-.8.3-1.2.5c-44.8 18.9-85 46-119.5 80.6a375.6 375.6 0 0 0-80.6 119.5A371.7 371.7 0 0 0 136 901.8a8 8 0 0 0 8 8.2h60c4.4 0 7.9-3.5 8-7.8c2-77.2 33-149.5 87.8-204.3c56.7-56.7 132-87.9 212.2-87.9s155.5 31.2 212.2 87.9C779 752.7 810 825 812 902.2c.1 4.4 3.6 7.8 8 7.8h60a8 8 0 0 0 8-8.2c-1-47.8-10.9-94.3-29.5-138.2M512 534c-45.9 0-89.1-17.9-121.6-50.4S340 407.9 340 362s17.9-89.1 50.4-121.6S466.1 190 512 190s89.1 17.9 121.6 50.4S684 316.1 684 362s-17.9 89.1-50.4 121.6S557.9 534 512 534"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // 10. Profile Filled: boxicons--user-filled.svg (24x24)
    val ProfileFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ProfileFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("M12 2a5 5 0 1 0 0 10a5 5 0 1 0 0-10M4 22h16c.55 0 1-.45 1-1v-1c0-3.86-3.14-7-7-7h-4c-3.86 0-7 3.14-7 7v1c0 .55.45 1 1 1"),
                fill = SolidColor(Color.White)
            )
        }.build()
    }

    // Settings
    val SettingsOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "SettingsOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6m7.4-2a7.5 7.5 0 0 0 0-2l2.1-1.5-2-3.5-2.5 1a7.5 7.5 0 0 0-1.7-1L14.8 3.5h-5.6l-.5 2.5a7.5 7.5 0 0 0-1.7 1l-2.5-1-2 3.5 2.1 1.5a7.5 7.5 0 0 0 0 2L2.5 14.5l2 3.5 2.5-1a7.5 7.5 0 0 0 1.7 1l.5 2.5h5.6l.5-2.5a7.5 7.5 0 0 0 1.7-1l2.5 1 2-3.5z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }

    val SettingsFilled: ImageVector by lazy {
        SettingsOutline
    }

    // 11. Camera Outline: Instagram Reels Camera Header Icon (24x24)
    val CameraOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "CameraOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = parseNodes("M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = parseNodes("M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }
}
