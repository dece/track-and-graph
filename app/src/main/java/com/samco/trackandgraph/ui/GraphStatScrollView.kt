package com.samco.trackandgraph.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.samco.trackandgraph.databinding.GraphStatScrollViewBinding
import com.samco.trackandgraph.databinding.GraphStatViewBinding


open class GraphStatScrollView : GraphStatViewBase {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrSet: AttributeSet) : super(context, attrSet)

    override fun getBinding(): GraphStatViewBinding {
        return GraphStatScrollViewBinding.inflate(LayoutInflater.from(context), this, true).graphStatView
    }
}
