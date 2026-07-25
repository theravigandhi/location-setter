package com.locationsetter.app

import android.app.Application
import com.locationsetter.app.di.AppContainer

class LocationSetterApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
