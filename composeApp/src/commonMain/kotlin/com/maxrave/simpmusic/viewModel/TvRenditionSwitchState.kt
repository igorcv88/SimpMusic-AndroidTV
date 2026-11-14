package com.maxrave.simpmusic.viewModel

enum class TvRenditionSwitchPhase { Idle, Pending, Unavailable, Unsupported, Error }

data class TvRenditionSwitchState(
    val mediaId: String? = null,
    val phase: TvRenditionSwitchPhase = TvRenditionSwitchPhase.Idle,
)
