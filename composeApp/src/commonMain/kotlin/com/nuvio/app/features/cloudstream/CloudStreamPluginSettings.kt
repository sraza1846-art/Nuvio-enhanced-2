package com.nuvio.app.features.cloudstream

/**
 * Host-side model of a .cs3 plugin's own settings, as shown by the
 * host-built Plugin Settings dialog.
 *
 * Only CineStream's schema is known (verified against its public source,
 * SaurabhKaperwan/CSX): every value below maps to a key the plugin itself
 * reads from CloudStream's DataStore (`CloudStreamApp.getKey/setKey`), so
 * edits take effect in the plugin. Other plugins have no known schema and
 * get no dialog instead of a fake one.
 */
data class Cs3PluginSettings(
    val downloadOnlyLinks: Boolean,
    val scrapingConcurrency: Int,
    val bypassDomains: List<Cs3BypassDomain>,
    val showboxToken: String,
    val wyzieSubsKey: String,
    val gramCinemaToken: String,
    val catalogCineStream: Boolean,
    val catalogSimkl: Boolean,
    val catalogTmdb: Boolean,
    val newProviderDefaultOn: Boolean,
    /** Fixed declared order; the dialog shows plain toggles, no reorder. */
    val providers: List<Cs3ProviderSetting>,
    val stremioAddons: List<Cs3StremioAddon>,
)

data class Cs3BypassDomain(
    /** Normalized domain used for the stored cookie key (`cf_cookie_<domain>`). */
    val domain: String,
    val displayName: String,
    val hasSavedCookie: Boolean,
)

data class Cs3ProviderSetting(
    /** DataStore key, e.g. `p_torrentio` or `stremio_torrentio`. */
    val key: String,
    val displayName: String,
    val enabled: Boolean,
    val isStremio: Boolean,
)

data class Cs3StremioAddon(
    val name: String,
    val url: String,
    /** One of HTTPS, TORRENT, DEBRID, SUBTITLE. */
    val type: String,
)
