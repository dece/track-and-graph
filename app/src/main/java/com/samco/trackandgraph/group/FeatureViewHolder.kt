/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.samco.trackandgraph.group

import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.DisplayTracker
import com.samco.trackandgraph.base.helpers.formatDayMonthYearHourMinute
import com.samco.trackandgraph.base.helpers.formatTimeDuration
import com.samco.trackandgraph.databinding.ListItemFeatureBinding
import org.threeten.bp.Duration
import org.threeten.bp.Instant

class FeatureViewHolder private constructor(
    private val binding: ListItemFeatureBinding,
) : GroupChildViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
    private var clickListener: FeatureClickListener? = null
    private var feature: DisplayTracker? = null
    private var dropElevation = 0f

    fun bind(feature: DisplayTracker, clickListener: FeatureClickListener) {
        this.feature = feature
        this.clickListener = clickListener
        this.dropElevation = binding.cardView.cardElevation
        setLastDateText()
        setNumEntriesText()
        binding.trackGroupNameText.text = feature.name
        binding.menuButton.setOnClickListener { createContextMenu(binding.menuButton) }
        binding.cardView.setOnClickListener { clickListener.onHistory(feature) }
        initAddButton(feature, clickListener)
        initTimerControls(feature, clickListener)
    }

    private fun initTimerControls(feature: DisplayTracker, clickListener: FeatureClickListener) {
        binding.playStopButtons.visibility =
            if (feature.dataType == DataType.DURATION) View.VISIBLE
            else View.GONE
        binding.playTimerButton.setOnClickListener {
            clickListener.onPlayTimer(feature)
        }
        binding.stopTimerButton.setOnClickListener {
            clickListener.onStopTimer(feature)
        }
        binding.playTimerButton.visibility =
            if (feature.timerStartInstant == null) View.VISIBLE else View.GONE
        binding.stopTimerButton.visibility =
            if (feature.timerStartInstant == null) View.GONE else View.VISIBLE

        if (feature.timerStartInstant != null) {
            updateTimerText()
            binding.timerText.visibility = View.VISIBLE
        } else {
            binding.timerText.visibility = View.GONE
            binding.timerText.text = formatTimeDuration(0)
        }
    }

    override fun update() {
        super.update()
        updateTimerText()
    }

    private fun updateTimerText() {
        feature?.timerStartInstant?.let {
            val duration = Duration.between(it, Instant.now())
            binding.timerText.text = formatTimeDuration(duration.seconds)
        }
    }

    private fun initAddButton(feature: DisplayTracker, clickListener: FeatureClickListener) {
        binding.addButton.setOnClickListener { clickListener.onAdd(feature) }
        binding.quickAddButton.setOnClickListener { onQuickAddClicked() }
        binding.quickAddButton.setOnLongClickListener {
            clickListener.onAdd(feature, false).let { true }
        }
        if (feature.hasDefaultValue) {
            binding.addButton.visibility = View.INVISIBLE
            binding.quickAddButton.visibility = View.VISIBLE
        } else {
            binding.addButton.visibility = View.VISIBLE
            binding.quickAddButton.visibility = View.INVISIBLE
        }
    }

    private fun setLastDateText() {
        val timestamp = feature?.timestamp
        binding.lastDateText.text = if (timestamp == null) {
            binding.lastDateText.context.getString(R.string.no_data)
        } else {
            formatDayMonthYearHourMinute(binding.lastDateText.context, timestamp)
        }
    }

    private fun setNumEntriesText() {
        val numDataPoints = feature?.numDataPoints
        binding.numEntriesText.text = if (numDataPoints != null) {
            binding.numEntriesText.context.getString(R.string.data_points, numDataPoints)
        } else {
            binding.numEntriesText.context.getString(R.string.no_data)
        }
    }

    private fun onQuickAddClicked() {
        val ripple = binding.cardView.foreground as RippleDrawable
        ripple.setHotspot(ripple.bounds.right.toFloat(), ripple.bounds.bottom.toFloat())
        ripple.state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
        ripple.state = intArrayOf()
        feature?.let { clickListener?.onAdd(it) }
    }

    override fun elevateCard() {
        binding.cardView.postDelayed({
            binding.cardView.cardElevation = binding.cardView.cardElevation * 3f
        }, 10)
    }

    override fun dropCard() {
        binding.cardView.cardElevation = dropElevation
    }

    private fun createContextMenu(view: View) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.edit_feature_context_menu, popup.menu)
        popup.setOnMenuItemClickListener(this)
        popup.show()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        feature?.let {
            when (item?.itemId) {
                R.id.edit -> clickListener?.onEdit(it)
                R.id.delete -> clickListener?.onDelete(it)
                R.id.moveTo -> clickListener?.onMoveTo(it)
                R.id.description -> clickListener?.onDescription(it)
                else -> {
                }
            }
        }
        return false
    }

    companion object {
        fun from(parent: ViewGroup): FeatureViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ListItemFeatureBinding.inflate(layoutInflater, parent, false)
            return FeatureViewHolder(binding)
        }
    }
}

class FeatureClickListener(
    private val onEditListener: (feature: DisplayTracker) -> Unit,
    private val onDeleteListener: (feature: DisplayTracker) -> Unit,
    private val onMoveToListener: (feature: DisplayTracker) -> Unit,
    private val onDescriptionListener: (feature: DisplayTracker) -> Unit,
    private val onAddListener: (feature: DisplayTracker, useDefault: Boolean) -> Unit,
    private val onHistoryListener: (feature: DisplayTracker) -> Unit,
    private val onPlayTimerListener: (feature: DisplayTracker) -> Unit,
    private val onStopTimerListener: (feature: DisplayTracker) -> Unit,
) {
    fun onEdit(feature: DisplayTracker) = onEditListener(feature)
    fun onDelete(feature: DisplayTracker) = onDeleteListener(feature)
    fun onMoveTo(feature: DisplayTracker) = onMoveToListener(feature)
    fun onDescription(feature: DisplayTracker) = onDescriptionListener(feature)
    fun onAdd(feature: DisplayTracker, useDefault: Boolean = true) =
        onAddListener(feature, useDefault)

    fun onHistory(feature: DisplayTracker) = onHistoryListener(feature)
    fun onPlayTimer(feature: DisplayTracker) = onPlayTimerListener(feature)
    fun onStopTimer(feature: DisplayTracker) = onStopTimerListener(feature)
}
