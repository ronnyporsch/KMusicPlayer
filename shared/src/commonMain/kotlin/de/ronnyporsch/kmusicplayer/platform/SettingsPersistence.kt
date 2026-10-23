package de.ronnyporsch.kmusicplayer.platform

import de.ronnyporsch.kmusicplayer.domain.model.AppSettings

expect fun saveSettings(settings: AppSettings)

expect fun loadSettings(): AppSettings?
