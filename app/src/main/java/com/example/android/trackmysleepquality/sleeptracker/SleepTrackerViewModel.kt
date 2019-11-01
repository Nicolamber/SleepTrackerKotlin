package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * View Model and coroutine implementation
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob = Job()

    /**
     * A [CoroutineScope] keeps track of all coroutines started by this ViewModel.
     *
     * Because we pass it [viewModelJob], any coroutine started in this uiScope can be cancelled
     * by calling `viewModelJob.cancel()`
     *
     * By default, all coroutines started in uiScope will launch in [Dispatchers.Main] which is
     * the main thread on Android. This is a sensible default because most coroutines started by
     * a [ViewModel] update the UI after performing some processing.
     */
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
     //variable yo hold the current night
    private var tonight = MutableLiveData<SleepNight?>()

    private val nights = database.getAllNights()

    //Executed every time we use the nights
    val nightsString = Transformations.map(nights) { nights ->
        formatNights(nights, application.resources)
    }


    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }

    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }


    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }


    private var _showSnackbarEvent = MutableLiveData<Boolean>()


    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent



    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    init {
        initializeTonight()
    }

    //Here is the coroutine related to the current night
    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    // suspend because we want to call it from coroutine
    // If I start the tracker, then close the app and after reopen, this coroutine do the work
    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }
    }

    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }

    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }

    private suspend fun insert(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(night)
        }
    }


     // Executes when the START button is clicked.

    fun onStartTracking() {
        uiScope.launch {

            // Create a new night, which captures the current time,
            // and insert it into the database.

            val newNight = SleepNight()

            insert(newNight)

            tonight.value = getTonightFromDatabase()
        }
    }


      //Executes when the STOP button is clicked.

    fun onStopTracking() {
        uiScope.launch {

            val oldNight = tonight.value ?: return@launch

            // Update the night in the database to add the end time.
            oldNight.endTimeMilli = System.currentTimeMillis()

            update(oldNight)

            // Set state to navigate to the SleepQualityFragment.
            _navigateToSleepQuality.value = oldNight
        }
    }


     // Executes when the CLEAR button is clicked.

    fun onClear() {
        uiScope.launch {
            // Clear the database table.
            clear()

            // And clear tonight since it's no longer in the database
            tonight.value = null
        }

        // Show a snackbar message, because it's friendly.
        _showSnackbarEvent.value = true
    }

    /**
     * Called when the ViewModel is dismantled.
     * At this point, we want to cancel all coroutines;
     * otherwise we end up with processes that have nowhere to return to
     * using memory and resources.
     */
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}
