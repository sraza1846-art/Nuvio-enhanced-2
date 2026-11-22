package com.nuvio.app.features.cloudstream

internal actual object CloudStreamPlatformRuntime {
    actual val supportsAndroidDex: Boolean = false

    actual fun initialize(context: Any?) = Unit
    actual suspend fun provider(plugin: CloudStreamPluginItem): CloudStreamProvider? = null
    // No syncDynamicRepositories: the expect object declares no such member
    // and nothing calls it; the stale actual broke the iOS build.
    actual suspend fun registerNativeRepository(url: String, name: String, iconUrl: String?) = Unit
    actual suspend fun removeNativeRepository(url: String) = Unit
    actual fun unload(pluginId: String) = Unit
    actual fun clear() = Unit

    actual suspend fun getCs3PluginApis(plugin: CloudStreamPluginItem): List<Cs3PluginApi>? = null
    actual suspend fun openCs3PluginNativeSettings(plugin: CloudStreamPluginItem): Boolean = false
}
