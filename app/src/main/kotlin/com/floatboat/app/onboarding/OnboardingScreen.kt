package com.floatboat.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.floatboat.app.R
import com.floatboat.app.ui.theme.FloatboatTheme

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onGrantOverlay: () -> Unit,
    onGrantNotifications: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onIslandToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placementSettings: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PermissionCard(
                title = stringResource(R.string.overlay_step_title),
                body = stringResource(R.string.overlay_step_body),
                granted = state.overlayGranted,
                onGrant = onGrantOverlay,
            )
            if (state.showNotificationStep) {
                PermissionCard(
                    title = stringResource(R.string.notifications_step_title),
                    body = stringResource(R.string.notifications_step_body),
                    granted = state.notificationsGranted,
                    onGrant = onGrantNotifications,
                )
            }
            PermissionCard(
                title = stringResource(R.string.notification_access_step_title),
                body = stringResource(R.string.notification_access_step_body),
                granted = state.notificationAccessGranted,
                onGrant = onGrantNotificationAccess,
            )
            IslandCard(
                enabled = state.overlayGranted,
                running = state.islandRunning,
                onToggle = onIslandToggle,
            )
            if (state.overlayGranted) {
                placementSettings()
            }

            Text(
                text = stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (granted) {
                Text(
                    text = stringResource(R.string.status_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(onClick = onGrant) {
                    Text(stringResource(R.string.action_grant))
                }
            }
        }
    }
}

@Composable
private fun IslandCard(
    enabled: Boolean,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val switchDescription = stringResource(R.string.island_switch_description)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.island_step_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (enabled) {
                            R.string.island_step_body_ready
                        } else {
                            R.string.island_step_body_blocked
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = running,
                onCheckedChange = onToggle,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    FloatboatTheme {
        OnboardingScreen(
            state = OnboardingUiState(
                overlayGranted = true,
                showNotificationStep = true,
                notificationsGranted = false,
                notificationAccessGranted = false,
                islandRunning = false,
            ),
            onGrantOverlay = {},
            onGrantNotifications = {},
            onGrantNotificationAccess = {},
            onIslandToggle = {},
        )
    }
}
