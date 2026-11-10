package com.arcimage.app

import android.app.Application
import com.arcimage.app.data.archive.ArchiveRepository
import com.arcimage.app.data.archive.ArchiveRepositoryImpl
import com.arcimage.app.data.image.ImageRepository
import com.arcimage.app.data.image.ImageRepositoryImpl
import com.arcimage.app.data.update.AppUpdateRepository
import com.arcimage.app.data.update.AppUpdateRepositoryImpl

interface AppContainer {
    val archiveRepository: ArchiveRepository
    val imageRepository: ImageRepository
    val appUpdateRepository: AppUpdateRepository
}

class DefaultAppContainer(application: Application) : AppContainer {
    override val archiveRepository: ArchiveRepository by lazy {
        ArchiveRepositoryImpl(application.applicationContext)
    }
    override val imageRepository: ImageRepository by lazy {
        ImageRepositoryImpl(application.applicationContext)
    }
    override val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepositoryImpl(application.applicationContext)
    }
}

class ArcImageApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
