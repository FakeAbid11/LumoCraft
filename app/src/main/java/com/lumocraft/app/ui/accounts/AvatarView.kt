package com.lumocraft.app.ui.accounts

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lumocraft.app.R
import com.lumocraft.app.core.theme.LumoCraftTheme
import com.lumocraft.app.domain.avatar.AvatarGenerator

/**
 * Renders the deterministic avatar of [username].
 * The bitmap is regenerated only when the username changes; `FilterQuality.None`
 * keeps the pixels crisp when upscaled.
 */
@Composable
fun AvatarView(
    username: String,
    modifier: Modifier = Modifier,
    generator: AvatarGenerator = remember { AvatarGenerator() },
) {
    val avatar = remember(username, generator) { generator.generate(username) }
    val imageBitmap = remember(avatar) {
        val bitmap = Bitmap.createBitmap(avatar.size, avatar.size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(avatar.pixels, 0, avatar.size, 0, 0, avatar.size, avatar.size)
        bitmap.asImageBitmap()
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = stringResource(R.string.accounts_avatar_description, username),
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None
    )
}

@Preview(showBackground = true)
@Composable
private fun AvatarPreview() {
    LumoCraftTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarView(username = "Steve_99", modifier = Modifier.size(64.dp))
            AvatarView(username = "LumoCraft", modifier = Modifier.size(64.dp))
            AvatarView(username = "alex2013", modifier = Modifier.size(64.dp))
        }
    }
}
