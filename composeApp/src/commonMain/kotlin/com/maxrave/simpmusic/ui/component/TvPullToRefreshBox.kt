package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maxrave.simpmusic.ui.tv.LocalTvDevice

/** TV scrolling never installs the touch refresh gesture or its indicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState,
    enabled: Boolean = true,
    indicator: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalTvDevice.current) {
        Box(modifier = modifier, content = content)
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            state = state,
            enabled = enabled,
            indicator = indicator,
            content = content,
        )
    }
}
