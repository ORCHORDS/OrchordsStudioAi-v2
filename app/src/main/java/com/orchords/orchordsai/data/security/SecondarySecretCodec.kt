package com.orchords.orchordsai.data.security

import com.orchords.orchordsai.data.datastore.Settings

object SecondarySecretCodec {
    fun redactSettingsForWrite(
        settings: Settings,
        store: SecondarySecretBackend,
    ): Settings? {
        val secrets = listOf(
            SecondarySecretKey.WEBDAV_PASSWORD to settings.webDavConfig.password,
            SecondarySecretKey.S3_SECRET_ACCESS_KEY to settings.s3Config.secretAccessKey,
            SecondarySecretKey.PROXY_USERNAME to settings.networkSetting.proxyUsername,
            SecondarySecretKey.PROXY_PASSWORD to settings.networkSetting.proxyPassword,
            SecondarySecretKey.WEB_SERVER_ACCESS_PASSWORD to settings.webServerAccessPassword,
        )

        if (!store.isAvailable() && secrets.any { (_, value) -> value.isNotBlank() }) return null

        for ((name, value) in secrets) {
            val ok = if (value.isBlank()) store.remove(name) else store.put(name, value)
            if (!ok && store.isAvailable()) return null
        }

        return settings.copy(
            networkSetting = settings.networkSetting.copy(
                proxyUsername = "",
                proxyPassword = "",
            ),
            webDavConfig = settings.webDavConfig.copy(password = ""),
            s3Config = settings.s3Config.copy(secretAccessKey = ""),
            webServerAccessPassword = "",
        )
    }

    fun hydrateSettingsFromStore(
        settings: Settings,
        store: SecondarySecretBackend,
    ): Settings = settings.copy(
        networkSetting = settings.networkSetting.copy(
            proxyUsername = store.get(SecondarySecretKey.PROXY_USERNAME) ?: "",
            proxyPassword = store.get(SecondarySecretKey.PROXY_PASSWORD) ?: "",
        ),
        webDavConfig = settings.webDavConfig.copy(
            password = store.get(SecondarySecretKey.WEBDAV_PASSWORD) ?: "",
        ),
        s3Config = settings.s3Config.copy(
            secretAccessKey = store.get(SecondarySecretKey.S3_SECRET_ACCESS_KEY) ?: "",
        ),
        webServerAccessPassword = store.get(SecondarySecretKey.WEB_SERVER_ACCESS_PASSWORD) ?: "",
    )
}
