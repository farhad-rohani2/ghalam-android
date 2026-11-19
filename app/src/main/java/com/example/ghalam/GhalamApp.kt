package com.example.ghalam

import android.app.Application
import com.example.ghalam.data.Graph

class GhalamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
