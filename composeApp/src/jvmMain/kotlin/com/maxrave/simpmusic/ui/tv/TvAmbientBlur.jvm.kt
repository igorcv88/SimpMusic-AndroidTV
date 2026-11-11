package com.maxrave.simpmusic.ui.tv

import coil3.request.ImageRequest

internal actual fun ImageRequest.Builder.ambientBlur(): ImageRequest.Builder = this

internal actual fun coil3.Image.publishTo(scene: TvScene) = Unit
