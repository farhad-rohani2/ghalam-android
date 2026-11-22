package com.example.ghalam.teacher

import android.app.Application
import com.example.ghalam.teacher.data.Graph

class TeacherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // already signed in (app restarted or the phone rebooted): keep the background sync alive
        if (!Graph.token.value.isNullOrBlank()) Graph.scheduler.ensurePeriodic()
    }
}
