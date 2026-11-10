package com.floatboat.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.floatboat.app.R
import com.floatboat.app.island.IslandPreferences

/** Which events pop the island, per-app notification muting, and general behaviour. */
@Composable
fun IslandSettingsCards(
    preferences: IslandPreferences,
    recentApps: Map<String, String>,
    notificationAccessGranted: Boolean,
    bluetoothGranted: Boolean,
    onChange: ((IslandPreferences) -> IslandPreferences) -> Unit,
    onAppMutedChange: (packageName: String, muted: Boolean) -> Unit,
) {
    val needsAccess = stringResource(R.string.events_needs_access)
        .takeUnless { notificationAccessGranted }
    SettingsCard(title = stringResource(R.string.events_title)) {
        SwitchRow(stringResource(R.string.events_torch), preferences.showTorch) { on ->
            onChange { it.copy(showTorch = on) }
        }
        SwitchRow(stringResource(R.string.events_charging), preferences.showCharging) { on ->
            onChange { it.copy(showCharging = on) }
        }
        SwitchRow(stringResource(R.string.events_media), preferences.showMedia, needsAccess) { on ->
            onChange { it.copy(showMedia = on) }
        }
        SwitchRow(stringResource(R.string.events_calls), preferences.showCalls, needsAccess) { on ->
            onChange { it.copy(showCalls = on) }
        }
        SwitchRow(
            stringResource(R.string.events_notifications),
            preferences.showNotifications,
            needsAccess,
        ) { on -> onChange { it.copy(showNotifications = on) } }
        SwitchRow(
            stringResource(R.string.events_bluetooth),
            preferences.showBluetooth,
            stringResource(R.string.events_needs_nearby).takeUnless { bluetoothGranted },
        ) { on -> onChange { it.copy(showBluetooth = on) } }
    }

    // Muted apps stay listed even if they have not posted since the app last started.
    val apps = preferences.mutedPackages.associateWith { it } + recentApps
    if (apps.isNotEmpty() && preferences.showNotifications) {
        SettingsCard(
            title = stringResource(R.string.apps_title),
            body = stringResource(R.string.apps_body),
        ) {
            apps.entries.sortedBy { it.value.lowercase() }.forEach { (packageName, label) ->
                SwitchRow(
                    title = label.ifBlank { packageName },
                    checked = packageName !in preferences.mutedPackages,
                ) { on -> onAppMutedChange(packageName, !on) }
            }
        }
    }

    SettingsCard(title = stringResource(R.string.behaviour_title)) {
        SwitchRow(stringResource(R.string.behaviour_haptics), preferences.haptics) { on ->
            onChange { it.copy(haptics = on) }
        }
        SwitchRow(stringResource(R.string.behaviour_boot), preferences.startOnBoot) { on ->
            onChange { it.copy(startOnBoot = on) }
        }
        SwitchRow(
            stringResource(R.string.behaviour_fullscreen),
            preferences.hideInFullscreen,
        ) { on -> onChange { it.copy(hideInFullscreen = on) } }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            content()
        }
    }
}

/** A whole-row toggle, so the label is part of the touch target and is read with the switch. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    note: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
