package com.nuvio.app.features.cloudstream

internal interface CloudStreamProvider {
    val id: String
    suspend fun getMainPage(page: Int): List<Pair<String, List<CloudStreamSearchItem>>>
    suspend fun search(query: String): List<CloudStreamSearchItem>
    suspend fun loadByExternalId(externalId: String): CloudStreamLoadItem? = null
    suspend fun load(data: String): CloudStreamLoadItem
    suspend fun loadLinks(data: String): List<CloudStreamPlaybackSource>
}

internal expect object CloudStreamPlatformRuntime {
    val supportsAndroidDex: Boolean

    fun initialize(context: Any?)
    suspend fun provider(plugin: CloudStreamPluginItem): CloudStreamProvider?
    fun unload(pluginId: String)
    suspend fun registerNativeRepository(url: String, name: String, iconUrl: String?)
    suspend fun removeNativeRepository(url: String)
    fun clear()

    /**
     * Reads a .cs3 plugin's own settings (CineStream's DataStore keys:
     * scraping, tokens, catalogs, provider toggles, Stremio addons) for the
     * host-built Plugin Settings dialog. Returns null when the plugin has no
     * settings schema the host knows (only CineStream's is known, verified
     * against its public source). CS3 plugins are DEX code with no JS, so the
     * JS `PluginRuntime.getPluginSettingsLayout` path cannot serve them.
     */
    suspend fun getCs3PluginSettings(plugin: CloudStreamPluginItem): Cs3PluginSettings?

    /** Persists an edited [Cs3PluginSettings] back to the plugin's DataStore keys. */
    fun saveCs3PluginSettings(plugin: CloudStreamPluginItem, settings: Cs3PluginSettings)

    /** Deletes a saved Cloudflare-bypass cookie for one bypass domain. */
    fun removeCs3PluginBypassCookie(domain: String)

    /**
     * Opens a .cs3 plugin's own native settings UI through its `openSettings`
     * entry point (the "CloudStream UI" gear-button mode). Returns true when
     * the plugin exposed an entry point and it was invoked without throwing;
     * false when the plugin has no entry point, is not installed/runnable, or
     * invoking it failed — the caller then falls back to the host-built
     * Nuvio dialog. The plugin's UI (e.g. CineStream's platform AlertDialog)
     * needs an Activity window token, so this runs on the main thread with
     * the host activity the runtime was initialized with.
     */
    suspend fun openCs3PluginNativeSettings(plugin: CloudStreamPluginItem): Boolean
}
