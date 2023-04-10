/*
* This file is part of Track & Graph
*
* Track & Graph is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Track & Graph is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.samco.trackandgraph.graphstatinput.configviews

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.PieChart
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.graphstatinput.GraphStatConfigEvent
import com.samco.trackandgraph.graphstatinput.customviews.SampleEndingAt
import com.samco.trackandgraph.graphstatinput.dtos.GraphStatDurations
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class PieChartConfigViewModel @Inject constructor(
    @IODispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val default: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher,
    gsiProvider: GraphStatInteractorProvider,
    dataInteractor: DataInteractor,
    private val timeRangeConfigBehaviour: TimeRangeConfigBehaviourImpl = TimeRangeConfigBehaviourImpl(),
    private val singleFeatureConfigBehaviour: SingleFeatureConfigBehaviourImpl = SingleFeatureConfigBehaviourImpl()
) : GraphStatConfigViewModelBase<GraphStatConfigEvent.ConfigData.PieChartConfigData>(
    io,
    default,
    ui,
    gsiProvider,
    dataInteractor
), TimeRangeConfigBehaviour by timeRangeConfigBehaviour,
    SingleFeatureConfigBehaviour by singleFeatureConfigBehaviour {

    init {
        timeRangeConfigBehaviour.initTimeRangeConfigBehaviour { onUpdate() }
        singleFeatureConfigBehaviour.initSingleFeatureConfigBehaviour(onUpdate = { onUpdate() })
    }

    var sumByCount: Boolean by mutableStateOf(false)
        private set

    private var pieChart = PieChart(
        id = 0L,
        graphStatId = 0L,
        featureId = -1L,
        duration = null,
        endDate = null,
        sumByCount = false
    )

    override fun updateConfig() {
        pieChart = pieChart.copy(
            featureId = this.featureId ?: -1L,
            duration = selectedDuration.duration,
            endDate = sampleEndingAt.asDateTime(),
            sumByCount = sumByCount
        )
    }

    override fun getConfig(): GraphStatConfigEvent.ConfigData.PieChartConfigData {
        return GraphStatConfigEvent.ConfigData.PieChartConfigData(pieChart)
    }

    override suspend fun validate(): GraphStatConfigEvent.ValidationException? {
        val id = pieChart.featureId
        if (id == -1L) {
            return GraphStatConfigEvent.ValidationException(R.string.graph_stat_validation_no_line_graph_features)
        }
        return null
    }

    override fun onDataLoaded(config: Any?) {
        singleFeatureConfigBehaviour.iniFeatureMap(featurePathProvider.sortedFeatureMap())

        if (config !is PieChart) return
        pieChart = config
        timeRangeConfigBehaviour.selectedDuration = GraphStatDurations.fromDuration(config.duration)
        timeRangeConfigBehaviour.sampleEndingAt = SampleEndingAt.fromDateTime(config.endDate)
        singleFeatureConfigBehaviour.featureId = config.featureId
        sumByCount = config.sumByCount
    }

    fun updateSumByCount(sumByCount: Boolean) {
        this.sumByCount = sumByCount
        onUpdate()
    }
}