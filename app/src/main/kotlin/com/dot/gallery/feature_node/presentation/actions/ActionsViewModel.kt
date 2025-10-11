package com.dot.gallery.feature_node.presentation.actions

import androidx.lifecycle.ViewModel
import com.dot.gallery.core.MediaDistributor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val distributor: MediaDistributor
): ViewModel() {

}