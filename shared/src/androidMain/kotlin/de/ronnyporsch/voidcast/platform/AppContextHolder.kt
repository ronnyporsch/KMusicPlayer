package de.ronnyporsch.voidcast.platform

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AppContextHolder {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
