package com.huevoman77.cazahosts

import android.app.Application
import com.huevoman77.cazahosts.data.ScanRepository

class CazaHostsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScanRepository.init(this)
    }
}
