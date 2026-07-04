package com.synapse.mobile

import android.app.Application
import com.tencent.mmkv.MMKV

class SynapseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }
}
