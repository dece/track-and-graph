package com.samco.trackandgraph.addtracker

import androidx.lifecycle.*
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.Tracker
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.TrackerHelper
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.ui.compose.viewmodels.DurationInputViewModel
import com.samco.trackandgraph.ui.compose.viewmodels.DurationInputViewModelImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AddTrackerViewModel : DurationInputViewModel {
    //Outputs
    val trackerName: LiveData<String>
    val trackerDescription: LiveData<String>
    val isDuration: LiveData<Boolean>
    val isLoading: LiveData<Boolean>
    val hasDefaultValue: LiveData<Boolean>
    val defaultValue: LiveData<Double>
    val defaultLabel: LiveData<String>
    val createButtonEnabled: LiveData<Boolean>
    val errorText: LiveData<Int?>
    val durationNumericConversionMode: LiveData<TrackerHelper.DurationNumericConversionMode>
    val isUpdateMode: LiveData<Boolean>

    //Inputs
    fun onTrackerNameChanged(name: String)
    fun onTrackerDescriptionChanged(description: String)
    fun onIsDurationCheckChanged(isDuration: Boolean)
    fun onHasDefaultValueChanged(hasDefaultValue: Boolean)
    fun onDefaultValueChanged(defaultValue: Double)
    fun onDefaultLabelChanged(defaultLabel: String)
    fun onDurationNumericConversionModeChanged(durationNumericConversionMode: TrackerHelper.DurationNumericConversionMode)
    fun onCreateClicked()
}

@HiltViewModel
class AddTrackerViewModelImpl @Inject constructor(
    private val dataInteractor: DataInteractor,
    @IODispatcher private val io: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher,
) : ViewModel(), AddTrackerViewModel, DurationInputViewModel by DurationInputViewModelImpl() {

    private var disallowedNames: List<String>? = null

    private sealed interface ValidationError {
        object NoName : ValidationError
        object NameAlreadyExists : ValidationError
    }

    private val trackerNameFlow = MutableStateFlow("")
    override val trackerName: LiveData<String> =
        trackerNameFlow.asLiveData(viewModelScope.coroutineContext)
    override val trackerDescription = MutableLiveData("")
    override val isDuration = MutableLiveData(false)
    override val isLoading = MutableLiveData(false)
    override val hasDefaultValue = MutableLiveData(false)
    override val defaultValue = MutableLiveData(1.0)
    override val defaultLabel = MutableLiveData("")

    private val validationErrorFlow = trackerNameFlow.map {
        when {
            it.isBlank() -> ValidationError.NoName
            disallowedNames?.contains(it) == true -> ValidationError.NameAlreadyExists
            else -> null
        }
    }
    override val createButtonEnabled = validationErrorFlow
        .map { it == null }
        .asLiveData(viewModelScope.coroutineContext)
    override val errorText = validationErrorFlow
        .map {
            when (it) {
                ValidationError.NoName -> R.string.tracker_name_cannot_be_null
                ValidationError.NameAlreadyExists -> R.string.tracker_with_that_name_exists
                else -> null
            }
        }
        .asLiveData(viewModelScope.coroutineContext)
    override val durationNumericConversionMode =
        MutableLiveData(TrackerHelper.DurationNumericConversionMode.HOURS)
    override val isUpdateMode = MutableLiveData(false)
    val complete = MutableLiveData(false)

    private var groupId: Long = -1
    private var existingTracker: Tracker? = null
    private var initialized = false

    fun init(groupId: Long, existingTrackerId: Long) {
        if (initialized) return
        initialized = true

        this.groupId = groupId
        viewModelScope.launch(io) {
            withContext(ui) { isLoading.value = true }
            dataInteractor.getTrackerById(existingTrackerId)?.let {
                initFromTracker(it)
                withContext(ui) { isUpdateMode.value = true }
            }
            disallowedNames = dataInteractor
                .getFeaturesForGroupSync(groupId)
                .map { it.name }
                .filter { it != existingTracker?.name }
            withContext(ui) { isLoading.value = false }
        }
    }

    private suspend fun initFromTracker(tracker: Tracker) = withContext(ui) {
        trackerNameFlow.value = tracker.name
        trackerDescription.value = tracker.description
        isDuration.value = tracker.dataType == DataType.DURATION
        hasDefaultValue.value = tracker.hasDefaultValue
        defaultValue.value = tracker.defaultValue
        defaultLabel.value = tracker.defaultLabel
    }

    override fun onTrackerNameChanged(name: String) {
        viewModelScope.launch(ui) {
            trackerNameFlow.emit(name)
        }
    }

    override fun onTrackerDescriptionChanged(description: String) {
        trackerDescription.value = description
    }

    override fun onIsDurationCheckChanged(isDuration: Boolean) {
        this.isDuration.value = isDuration
    }

    override fun onHasDefaultValueChanged(hasDefaultValue: Boolean) {
        this.hasDefaultValue.value = hasDefaultValue
    }

    override fun onDefaultValueChanged(defaultValue: Double) {
        this.defaultValue.value = defaultValue
    }

    override fun onDefaultLabelChanged(defaultLabel: String) {
        this.defaultLabel.value = defaultLabel
    }

    override fun onDurationNumericConversionModeChanged(durationNumericConversionMode: TrackerHelper.DurationNumericConversionMode) {
        this.durationNumericConversionMode.value = durationNumericConversionMode
    }

    override fun onCreateClicked() {
        viewModelScope.launch(io) {
            withContext(ui) { isLoading.value = true }
            existingTracker?.let {
                updateTracker(it)
            } ?: addTracker()
            withContext(ui) { isLoading.value = false }
            withContext(ui) { complete.value = true }
        }
    }

    private fun getDataType() = when (isDuration.value) {
        true -> DataType.DURATION
        else -> DataType.CONTINUOUS
    }

    private fun getDefaultValue() = when (isDuration.value) {
        true -> getDurationAsDouble()
        else -> defaultValue.value
    }

    private suspend fun updateTracker(existingTracker: Tracker) {
        dataInteractor.updateTracker(
            oldTracker = existingTracker,
            durationNumericConversionMode = durationNumericConversionMode.value,
            newName = trackerName.value,
            newType = getDataType(),
            hasDefaultValue = hasDefaultValue.value,
            defaultValue = getDefaultValue(),
            featureDescription = trackerDescription.value,
            defaultLabel = defaultLabel.value
        )
    }

    private suspend fun addTracker() {
        val tracker = Tracker(
            id = 0L,
            name = trackerName.value ?: "",
            groupId = groupId,
            featureId = 0L,
            displayIndex = 0,
            description = trackerDescription.value ?: "",
            dataType = getDataType(),
            hasDefaultValue = hasDefaultValue.value ?: false,
            defaultValue = getDefaultValue() ?: 1.0,
            defaultLabel = defaultLabel.value ?: ""
        )
        dataInteractor.insertTracker(tracker)
    }
}