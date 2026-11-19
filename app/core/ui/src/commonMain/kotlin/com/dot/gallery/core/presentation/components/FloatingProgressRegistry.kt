/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


/**
 * App-wide list of floating progress tasks. Producers [post] and [dismiss] by id; the newest
 * task is the one [FloatingProgressHost] shows.
 */
class FloatingProgressRegistry {
    private val _tasks = MutableStateFlow<List<FloatingProgressTask>>(emptyList())
    val tasks: StateFlow<List<FloatingProgressTask>> = _tasks.asStateFlow()

    /** Adds [task], or replaces the task with the same id in place. */
    fun post(task: FloatingProgressTask) {
        _tasks.update { current ->
            val index = current.indexOfFirst { it.id == task.id }
            if (index < 0) current + task
            else current.toMutableList().also { it[index] = task }
        }
    }

    fun update(id: String, transform: (FloatingProgressTask) -> FloatingProgressTask) {
        _tasks.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    fun dismiss(id: String) {
        _tasks.update { current -> current.filterNot { it.id == id } }
    }
}
