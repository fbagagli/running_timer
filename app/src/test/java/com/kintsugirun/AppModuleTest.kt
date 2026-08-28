package com.kintsugirun

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get

@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleTest : KoinTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        startKoin {
            androidContext(mockApplication)
            modules(appModule)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `verify appModule resolves dependencies properly`() {
        val repository: WorkoutRepository = get()
        assertThat(repository).isNotNull()

        val homeViewModel: HomeViewModel = get()
        assertThat(homeViewModel).isNotNull()

        val reviewViewModel: WorkoutReviewViewModel = get()
        assertThat(reviewViewModel).isNotNull()

        val editorViewModel: WorkoutEditorViewModel = get { parametersOf(1L) }
        assertThat(editorViewModel).isNotNull()

        val activeViewModel: ActiveWorkoutViewModel = get()
        assertThat(activeViewModel).isNotNull()
    }
}
