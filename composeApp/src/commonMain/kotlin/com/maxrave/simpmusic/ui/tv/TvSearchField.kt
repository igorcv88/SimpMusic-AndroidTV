package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo

/** A real editor; TV navigation adds key handling without adding a second focus target. */
@Composable
internal fun TvSearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        state = state,
        modifier = modifier.tvTextInput(onActivate = onActivate),
        lineLimits = TextFieldLineLimits.SingleLine,
        // The state-based editor must create its input session on real focus. Calling show()
        // on a keyboard controller cannot establish the session disabled by a false value.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = true),
        onKeyboardAction = {
            keyboardController?.hide()
            onSearch(state.text.toString())
        },
        placeholder = { Text(placeholder, style = typo().labelMedium) },
        leadingIcon = { Icon(SimpIcons.Search, contentDescription = null) },
        shape = TvShapes.Control,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = TvPalette.GlassFill,
            cursorColor = TvPalette.TextPrimary,
            focusedTextColor = TvPalette.TextPrimary,
            unfocusedTextColor = TvPalette.TextPrimary,
            focusedPlaceholderColor = TvPalette.TextSecondary,
            unfocusedPlaceholderColor = TvPalette.TextSecondary,
            focusedLeadingIconColor = TvPalette.TextPrimary,
            unfocusedLeadingIconColor = TvPalette.TextSecondary,
        ),
    )
}
