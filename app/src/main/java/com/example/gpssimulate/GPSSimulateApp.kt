package com.example.gpssimulate

import android.app.Application
import org.osmdroid.config.Configuration

class GPSSimulateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
    }
}
