package com.huevoman77.cazahosts

import android.app.Application
import com.huevoman77.cazahosts.data.ScanRepository
import com.huevoman77.cazahosts.util.CrashLog

class CazaHostsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ScanRepository.init(this)
    }
}
