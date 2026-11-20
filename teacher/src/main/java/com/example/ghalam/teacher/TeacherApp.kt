package com.example.ghalam.teacher

import android.app.Application
import com.example.ghalam.teacher.data.Graph

class TeacherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
