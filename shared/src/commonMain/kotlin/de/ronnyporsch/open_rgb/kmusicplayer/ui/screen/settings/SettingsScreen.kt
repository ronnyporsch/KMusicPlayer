package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.YT_DLP_COOKIE_BROWSERS
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifySurface
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("\u2190", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Server Section
            SectionHeader("Server Connection")

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = { viewModel.onEvent(SettingsEvent.ServerUrlChanged(it)) },
                label = { Text("Server URL") },
                placeholder = { Text("https://navidrome.example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = settingsTextFieldColors(),
                shape = RoundedCornerShape(0.dp),
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.onEvent(SettingsEvent.UsernameChanged(it)) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = settingsTextFieldColors(),
                shape = RoundedCornerShape(0.dp),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.onEvent(SettingsEvent.PasswordChanged(it)) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = settingsTextFieldColors(),
                shape = RoundedCornerShape(0.dp),
            )

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.reconnectSuccess) {
                Text(
                    text = "Connected successfully",
                    color = SpotifyGreen,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = { viewModel.onEvent(SettingsEvent.Reconnect) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(0.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                enabled = !state.isReconnecting,
            ) {
                if (state.isReconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Reconnect", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Playback Section
            SectionHeader("Playback")

            SettingsSwitch(
                title = "Gapless Playback",
                subtitle = "Pre-buffer the next track for seamless transitions",
                checked = state.gaplessEnabled,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.GaplessToggled(it)) },
            )

            SettingsSwitch(
                title = "Crossfade",
                subtitle = "Smoothly fade between tracks",
                checked = state.crossfadeEnabled,
                onCheckedChange = { viewModel.onEvent(SettingsEvent.CrossfadeToggled(it)) },
            )

            if (state.crossfadeEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = "Crossfade duration: ${state.crossfadeDurationSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.crossfadeDurationSeconds.toFloat(),
                        onValueChange = {
                            viewModel.onEvent(SettingsEvent.CrossfadeDurationChanged(it.roundToInt()))
                        },
                        valueRange = 1f..12f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = SpotifyGreen,
                            activeTrackColor = SpotifyGreen,
                            inactiveTrackColor = SpotifySurface,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("1s", style = MaterialTheme.typography.bodySmall, color = SpotifyLightGray)
                        Text("12s", style = MaterialTheme.typography.bodySmall, color = SpotifyLightGray)
                    }
                }
            }

            if (state.youTubeAvailable) {
                Spacer(Modifier.height(16.dp))

                SectionHeader("YouTube Music")

                OutlinedTextField(
                    value = state.youTubeDownloadDir,
                    onValueChange = { viewModel.onEvent(SettingsEvent.YouTubeDownloadDirChanged(it)) },
                    label = { Text("Download folder") },
                    placeholder = { Text("/srv/music") },
                    supportingText = {
                        Text("The server's music folder (local or mounted). Downloads are saved in their original format as Artist/Album/Title.opus (or .m4a)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = settingsTextFieldColors(),
                    shape = RoundedCornerShape(0.dp),
                )

                CookiesBrowserDropdown(
                    selected = state.youTubeCookiesBrowser,
                    onSelected = { viewModel.onEvent(SettingsEvent.YouTubeCookiesBrowserChanged(it)) },
                )
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookiesBrowserDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun label(browser: String) = if (browser.isBlank()) "None" else browser.replaceFirstChar { it.uppercase() }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Use cookies from browser") },
            supportingText = {
                Text("Lets yt-dlp use your YouTube login from this browser, e.g. for Premium audio quality and to avoid bot checks")
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            colors = settingsTextFieldColors(),
            shape = RoundedCornerShape(0.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf("") + YT_DLP_COOKIE_BROWSERS).forEach { browser ->
                DropdownMenuItem(
                    text = { Text(label(browser)) },
                    onClick = {
                        onSelected(browser)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SpotifyGreen,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpotifyLightGray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SpotifyGreen,
                checkedTrackColor = SpotifyGreen.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SpotifyGreen,
    cursorColor = SpotifyGreen,
    focusedLabelColor = SpotifyGreen,
)
