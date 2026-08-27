package com.kintsugirun

import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { WorkoutRepository(androidContext()) }
    single { TimerManager }

    viewModel { HomeViewModel(get()) }
    viewModel { WorkoutReviewViewModel(get()) }
    viewModel { (fileName: String?) -> WorkoutEditorViewModel(get(), fileName) }
    viewModel { ActiveWorkoutViewModel(get(), androidApplication()) }
}
