/*
 * Copyright (c) 2026 Auxio Project
 * CoverCompositionFetcher.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.image.coil

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toDrawable
import coil3.Bitmap
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.size.Size
import coil3.size.pxOrElse
import kotlin.math.min
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.oxycblt.musikr.covers.CoverCollection

interface CoverComposition {
    val covers: CoverCollection
}

abstract class CoverCompositionFetcher(
    private val context: Context,
    private val data: CoverComposition,
    private val size: Size,
) : Fetcher {
    final override suspend fun fetch(): FetchResult? {
        val bitmaps =
            data.covers.covers
                .asFlow()
                .mapNotNull { cover -> cover.open() }
                .mapNotNull { stream -> BitmapFactory.decodeStream(stream).also { stream.close() } }
                .take(4)
                .toList()
        if (bitmaps.size < 4) {
            val first = bitmaps.firstOrNull() ?: return null
            return ImageFetchResult(
                image = first.toDrawable(context.resources).asImage(),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        }

        val squareSize =
            min(size.width.pxOrElse { 512 }, size.height.pxOrElse { 512 }).coerceAtLeast(1)
        return ImageFetchResult(
            image = compose(bitmaps, squareSize).toDrawable(context.resources).asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    /**
     * Compose the given input cover data into a single FetchResult containing a composed cover
     * bitmap.
     *
     * @param bitmaps The bitmaps to use.
     * @param size The size of the square bitmap you should create.
     */
    protected abstract fun compose(bitmaps: List<Bitmap>, size: Int): Bitmap
}
