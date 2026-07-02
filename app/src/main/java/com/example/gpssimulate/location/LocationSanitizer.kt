package com.example.gpssimulate.location

import android.location.Location
import android.os.Build

/**
 * 尝试清除 Location 上的模拟标记，提升部分 App 对注入坐标的接受度。
 */
internal object LocationSanitizer {

    fun stripMockFlags(location: Location) {
        try {
            Location::class.java
                .getMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType)
                .invoke(location, false)
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Location::class.java
                    .getMethod("setMock", Boolean::class.javaPrimitiveType)
                    .invoke(location, false)
            } catch (_: Exception) {
            }
        }

        try {
            val field = Location::class.java.getDeclaredField("mIsFromMockProvider")
            field.isAccessible = true
            field.setBoolean(location, false)
        } catch (_: Exception) {
        }

        val extras = location.extras
        if (extras != null) {
            extras.remove("mockLocation")
            if (extras.isEmpty) {
                location.extras = null
            }
        }
    }
}
