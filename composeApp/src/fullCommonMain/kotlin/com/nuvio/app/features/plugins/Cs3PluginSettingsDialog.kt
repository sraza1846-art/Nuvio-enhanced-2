package com.nuvio.app.features.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.features.cloudstream.Cs3PluginSettings
import com.nuvio.app.features.cloudstream.Cs3StremioAddon

/**
 * Host-built Plugin Settings dialog for .cs3 plugins (CineStream).
 * Mirrors the sections of the plugin's own settings UI, backed by the exact
 * DataStore keys the plugin reads, so edits take effect in the plugin:
 * Scraping Settings, Cloudflare Bypass, API Tokens, Active Catalogs,
 * Providers (plain toggles in the plugin's declared order — no reorder),
 * Stremio Addons, Credits & Thanks.
 *
 * Deliberately separate from [PluginSettingsDialog] (JS scrapers): that
 * dialog renders plugin-provided JSON layouts, while this one renders the
 * host-known CS3 schema.
 */
@Composable
fun Cs3PluginSettingsDialog(
    initial: Cs3PluginSettings,
    onDismiss: () -> Unit,
    onSave: (Cs3PluginSettings) -> Unit,
    onRemoveBypassCookie: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    var edited by remember { mutableStateOf(initial) }
    var scrapingExpanded by rememberSaveable { mutableStateOf(false) }
    var cloudflareExpanded by rememberSaveable { mutableStateOf(false) }
    var tokensExpanded by rememberSaveable { mutableStateOf(false) }
    var catalogsExpanded by rememberSaveable { mutableStateOf(false) }
    var providersExpanded by rememberSaveable { mutableStateOf(false) }
    var addonsExpanded by rememberSaveable { mutableStateOf(false) }
    var creditsExpanded by rememberSaveable { mutableStateOf(false) }
    var providerQuery by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val providerQueryTrimmed = providerQuery.trim()
    val enabledProviderCount = edited.providers.count { it.enabled }
    val visibleProviders = remember(edited.providers, providerQueryTrimmed) {
        if (providerQueryTrimmed.isBlank()) edited.providers
        else edited.providers.filter { it.displayName.contains(providerQueryTrimmed, ignoreCase = true) }
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = "Plugin Settings",
        message = "Configure sources, catalogs & cookies",
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Cs3SettingsSection(
                title = "Scraping Settings",
                expanded = scrapingExpanded,
                onToggle = { scrapingExpanded = !scrapingExpanded },
            ) {
                Cs3ToggleRow(
                    label = "Download Only Links",
                    description = "Only great for downloading (Not for Streaming)",
                    checked = edited.downloadOnlyLinks,
                    onCheckedChange = { edited = edited.copy(downloadOnlyLinks = it) },
                )
                Cs3StepperRow(
                    label = "Scraping Concurrency",
                    description = "Number of providers run concurrently\n⚠️ For low end devices set it low",
                    value = edited.scrapingConcurrency,
                    onValueChange = { edited = edited.copy(scrapingConcurrency = it.coerceIn(1, 50)) },
                )
            }

            Cs3SettingsSection(
                title = "Cloudflare Bypass",
                expanded = cloudflareExpanded,
                onToggle = { cloudflareExpanded = !cloudflareExpanded },
            ) {
                Text(
                    text = "Cookies saved by solving a challenge let the plugin skip Cloudflare. " +
                        "Solving a new challenge opens in the plugin's own browser and is not available here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                edited.bypassDomains.forEach { domain ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(domain.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (domain.hasSavedCookie) "✓ Saved" else "Not Saved",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (domain.hasSavedCookie) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (domain.hasSavedCookie) {
                            OutlinedButton(onClick = {
                                onRemoveBypassCookie(domain.domain)
                                edited = edited.copy(
                                    bypassDomains = edited.bypassDomains.map {
                                        if (it.domain == domain.domain) it.copy(hasSavedCookie = false) else it
                                    },
                                )
                            }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            Cs3SettingsSection(
                title = "API Tokens",
                expanded = tokensExpanded,
                onToggle = { tokensExpanded = !tokensExpanded },
            ) {
                Cs3TokenField(
                    label = "Febbox Token",
                    description = "Febbox token to enable ShowBox source",
                    placeholder = "Paste UI token",
                    value = edited.showboxToken,
                    onValueChange = { edited = edited.copy(showboxToken = it) },
                )
                Cs3TokenField(
                    label = "Wyzie Subs API Key",
                    description = "Enter your Wyzie Subs API key to unlock subtitle sources",
                    placeholder = "Paste API key",
                    value = edited.wyzieSubsKey,
                    onValueChange = { edited = edited.copy(wyzieSubsKey = it) },
                    getKeyAction = { uriHandler.openUri("https://sub.wyzie.io/redeem") },
                )
                Cs3TokenField(
                    label = "GramCinema Bearer Token",
                    description = "Enter your GramCinema bearer token to enable the source",
                    placeholder = "Paste bearer token",
                    value = edited.gramCinemaToken,
                    onValueChange = { edited = edited.copy(gramCinemaToken = it) },
                )
            }

            Cs3SettingsSection(
                title = "Active Catalogs",
                expanded = catalogsExpanded,
                onToggle = { catalogsExpanded = !catalogsExpanded },
            ) {
                Cs3ToggleRow(
                    label = "CineStream",
                    description = "Cinemeta catalog",
                    checked = edited.catalogCineStream,
                    onCheckedChange = { edited = edited.copy(catalogCineStream = it) },
                )
                Cs3ToggleRow(
                    label = "CineSimkl",
                    description = "Simkl catalog",
                    checked = edited.catalogSimkl,
                    onCheckedChange = { edited = edited.copy(catalogSimkl = it) },
                )
                Cs3ToggleRow(
                    label = "CineTmdb",
                    description = "TMDB catalog",
                    checked = edited.catalogTmdb,
                    onCheckedChange = { edited = edited.copy(catalogTmdb = it) },
                )
                Text(
                    text = "Changing catalogs needs a full restart to take effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (edited.providers.isNotEmpty()) {
                Cs3SettingsSection(
                    title = "Providers",
                    trailing = "$enabledProviderCount / ${edited.providers.size} on",
                    expanded = providersExpanded,
                    onToggle = { providersExpanded = !providersExpanded },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                edited = edited.copy(
                                    providers = edited.providers.map { it.copy(enabled = true) },
                                )
                            },
                        ) { Text("All") }
                        OutlinedButton(
                            onClick = {
                                edited = edited.copy(
                                    providers = edited.providers.map { it.copy(enabled = false) },
                                )
                            },
                        ) { Text("None") }
                    }
                    Cs3ToggleRow(
                        label = "New Built-in Providers",
                        description = "When a new provider is added enable it automatically",
                        checked = edited.newProviderDefaultOn,
                        onCheckedChange = { edited = edited.copy(newProviderDefaultOn = it) },
                    )
                    Text(
                        text = "🧲 = off by default  ·  🔌 = Stremio addon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NuvioInputField(
                        value = providerQuery,
                        onValueChange = { providerQuery = it },
                        placeholder = "Search providers...",
                    )
                    visibleProviders.forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${edited.providers.indexOf(provider) + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = provider.enabled,
                                onCheckedChange = { checked ->
                                    edited = edited.copy(
                                        providers = edited.providers.map {
                                            if (it.key == provider.key) it.copy(enabled = checked) else it
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Cs3SettingsSection(
                title = "Stremio Addons",
                trailing = if (edited.stremioAddons.isEmpty()) null
                else "${edited.stremioAddons.size} addon${if (edited.stremioAddons.size == 1) "" else "s"}",
                expanded = addonsExpanded,
                onToggle = { addonsExpanded = !addonsExpanded },
            ) {
                Text(
                    text = "Only IMDB Supported Addons — Only addons that have support of IMDB " +
                        "& No catalog addons are supported.\n" +
                        "Tap the type badge on each addon to cycle between HTTPS → TORRENT → DEBRID → SUBTITLE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    edited = edited.copy(
                        stremioAddons = edited.stremioAddons + Cs3StremioAddon(name = "", url = "", type = "HTTPS"),
                    )
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Addon")
                }
                if (edited.stremioAddons.isEmpty()) {
                    Text(
                        text = "No addons yet — tap Add Addon to add one",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                edited.stremioAddons.forEachIndexed { index, addon ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val next = when (addon.type.uppercase()) {
                                        "HTTPS" -> "TORRENT"
                                        "TORRENT" -> "DEBRID"
                                        "DEBRID" -> "SUBTITLE"
                                        else -> "HTTPS"
                                    }
                                    edited = edited.copy(
                                        stremioAddons = edited.stremioAddons.mapIndexed { i, item ->
                                            if (i == index) item.copy(type = next) else item
                                        },
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) { Text(addon.type.uppercase()) }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                edited = edited.copy(
                                    stremioAddons = edited.stremioAddons.filterIndexed { i, _ -> i != index },
                                )
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove addon")
                            }
                        }
                        NuvioInputField(
                            value = addon.name,
                            onValueChange = { value ->
                                edited = edited.copy(
                                    stremioAddons = edited.stremioAddons.mapIndexed { i, item ->
                                        if (i == index) item.copy(name = value) else item
                                    },
                                )
                            },
                            placeholder = "Name (e.g. Torrentio)",
                        )
                        NuvioInputField(
                            value = addon.url,
                            onValueChange = { value ->
                                edited = edited.copy(
                                    stremioAddons = edited.stremioAddons.mapIndexed { i, item ->
                                        if (i == index) item.copy(url = value) else item
                                    },
                                )
                            },
                            placeholder = "https://xyz.com/manifest.json",
                        )
                    }
                    if (index < edited.stremioAddons.lastIndex) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Cs3SettingsSection(
                title = "Credits & Thanks",
                expanded = creditsExpanded,
                onToggle = { creditsExpanded = !creditsExpanded },
            ) {
                Text(
                    text = "Special thanks to these developers whose work served as reference and inspiration ❤️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Cs3CreditRow(
                    name = "phisher98",
                    role = "For multi-source plugin inspiration",
                    url = "https://github.com/phisher98",
                    onOpenUrl = uriHandler::openUri,
                )
                Cs3CreditRow(
                    name = "yogesh-hacker",
                    role = "For providing reference",
                    url = "https://github.com/yogesh-hacker",
                    onOpenUrl = uriHandler::openUri,
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = "Cancel",
                onClick = onDismiss,
            )
            DialogButton(
                text = "Save",
                onClick = {
                    val invalidIndex = edited.stremioAddons.indexOfFirst { it.url.isBlank() }
                    if (invalidIndex != -1) {
                        onMessage("Addon ${invalidIndex + 1} has an empty URL — addons were not saved.")
                        onSave(edited.copy(stremioAddons = initial.stremioAddons))
                    } else {
                        onSave(edited)
                    }
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun Cs3SettingsSection(
    title: String,
    trailing: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Cs3ToggleRow(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun Cs3StepperRow(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = { if (value > 1) onValueChange(value - 1) },
            contentPadding = PaddingValues(8.dp),
        ) { Text("-") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OutlinedButton(
            onClick = { if (value < 50) onValueChange(value + 1) },
            contentPadding = PaddingValues(8.dp),
        ) { Text("+") }
    }
}

@Composable
private fun Cs3TokenField(
    label: String,
    description: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    getKeyAction: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Text(
                    text = "✓ Saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            getKeyAction?.let {
                TextButton(onClick = it) { Text("Get Key") }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                    )
                }
            },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Cs3CreditRow(
    name: String,
    role: String,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onOpenUrl(url) }) { Text("GitHub") }
    }
}
