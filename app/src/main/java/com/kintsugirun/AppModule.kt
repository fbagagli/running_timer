package com.kintsugirun

import androidx.room.Room
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<TimeProvider> { SystemTimeProvider() }
    single { GpsTracker(androidContext()) }
    single { TimerManager(timeProvider = get(), repository = get(), gpsTracker = get()) }

    // Auto-Backup
    single { AutoBackupManager(androidContext()) }

    // Room Database
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "kintsugi_run.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<AppDatabase>().workoutDao() }
    single { get<AppDatabase>().workoutSessionDao() }
    single { WorkoutRepository(get(), get(), get()) }

    viewModel { HomeViewModel(get()) }
    viewModel { WorkoutReviewViewModel(get()) }
    viewModel { (workoutId: Long?) -> WorkoutEditorViewModel(get(), workoutId) }
    viewModel { ActiveWorkoutViewModel(get(), get(), androidApplication(), get()) }
}
