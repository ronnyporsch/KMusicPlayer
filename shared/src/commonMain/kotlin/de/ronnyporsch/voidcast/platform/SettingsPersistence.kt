package de.ronnyporsch.voidcast.platform

import de.ronnyporsch.voidcast.domain.model.AppSettings

expect fun saveSettings(settings: AppSettings)

expect fun loadSettings(): AppSettings?
