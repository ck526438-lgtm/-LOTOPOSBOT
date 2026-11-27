package com.example.lotobot

import android.app.Application
import com.example.lotobot.notif.Notifications

class LotoBotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.creerCanal(this)
    }
}
