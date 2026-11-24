package com.tuapp.tabatatrainer.di

import android.content.Context
import com.tuapp.tabatatrainer.data.local.AppDatabase
import com.tuapp.tabatatrainer.data.local.DeviceProfileDao
import com.tuapp.tabatatrainer.data.local.GpsDao
import com.tuapp.tabatatrainer.data.local.SensorReadingDao
import com.tuapp.tabatatrainer.data.local.SessionDao
import com.tuapp.tabatatrainer.rawlog.RawFiles
import com.tuapp.tabatatrainer.rawlog.RawLogExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    @Singleton
    fun provideSensorReadingDao(database: AppDatabase): SensorReadingDao {
        return database.sensorReadingDao()
    }

    @Provides
    @Singleton
    fun provideGpsDao(database: AppDatabase): GpsDao {
        return database.gpsDao()
    }

    @Provides
    @Singleton
    fun provideDeviceProfileDao(database: AppDatabase): DeviceProfileDao {
        return database.deviceProfileDao()
    }

    /** Carpetas del registro en bruto (spec 008, R13/R14): `files/rawlog/`. */
    @Provides
    @Singleton
    fun provideRawLogExporter(@ApplicationContext context: Context): RawLogExporter {
        return RawLogExporter(File(context.filesDir, RawFiles.ROOT_DIR))
    }

    // SoundPlayer, HeartRateHub, CadenceHub, AntRadio, and GpsManager
    // have @Inject constructor. Hilt already knows how to provide them.
}