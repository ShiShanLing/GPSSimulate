package com.example.gpssimulate

import android.app.Application
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.common.BaiduMapSDKException

class GPSSimulateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SDKInitializer.setAgreePrivacy(applicationContext, true)
        try {
            SDKInitializer.initialize(applicationContext)
            SDKInitializer.setCoordType(CoordType.BD09LL)
        } catch (_: BaiduMapSDKException) {
        }
    }
}
