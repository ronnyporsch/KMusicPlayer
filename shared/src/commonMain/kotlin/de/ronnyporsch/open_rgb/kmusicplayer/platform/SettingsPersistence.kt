package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AppSettings

expect fun saveSettings(settings: AppSettings)

expect fun loadSettings(): AppSettings?
