package com.example.mylock

import android.app.Application
import com.example.mylock.core.di.MyLockContainer

class MyLockApplication : Application() {
    lateinit var container: MyLockContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = MyLockContainer(this)
    }
}
