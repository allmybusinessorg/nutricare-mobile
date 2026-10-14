package com.nutricare.mobile

import android.app.Application
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.core.SessionStore

class NutriApp : Application() {
    companion object {
        lateinit var instance: NutriApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ServiceLocator.init(this)
        SessionStore.attach(this)
    }
}
