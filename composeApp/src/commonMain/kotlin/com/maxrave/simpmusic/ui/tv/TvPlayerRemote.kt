package com.maxrave.simpmusic.ui.tv

internal enum class TvPlayerKey { Left, Right, Up, Down, Activate, Back, Other }
internal enum class TvPlayerAction { Reveal, Navigate, Hide, Leave }

/** A wake-up key never also activates a transport button or changes the position. */
internal fun tvPlayerAction(visible: Boolean, key: TvPlayerKey): TvPlayerAction = when {
    key == TvPlayerKey.Back -> if (visible) TvPlayerAction.Hide else TvPlayerAction.Leave
    !visible && key != TvPlayerKey.Other -> TvPlayerAction.Reveal
    else -> TvPlayerAction.Navigate
}
