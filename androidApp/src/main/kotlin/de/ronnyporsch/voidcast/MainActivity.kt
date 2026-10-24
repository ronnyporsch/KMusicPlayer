package de.ronnyporsch.voidcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.ronnyporsch.voidcast.platform.AppContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AppContextHolder.init(this)

        setContent {
            App()
        }
    }
}
