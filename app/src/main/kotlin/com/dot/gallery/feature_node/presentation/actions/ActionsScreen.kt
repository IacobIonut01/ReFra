package com.dot.gallery.feature_node.presentation.actions

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ActionsScreen() {
    val viewModel = hiltViewModel<ActionsViewModel>()
}
