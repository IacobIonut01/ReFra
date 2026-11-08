/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import com.dot.gallery.cloud.core.PersonInfo

/** A chip on the People page. [key] is the provider section key for [Kind.PROVIDER]. */
data class PeopleFilter(val kind: Kind, val key: String? = null) {
    enum class Kind { ALL, NAMED, NEW, HIDDEN, PROVIDER }

    /** Stable string form for `rememberSaveable`. */
    fun encode(): String = if (key == null) kind.name else "${kind.name}:$key"

    companion object {
        val All = PeopleFilter(Kind.ALL)

        fun decode(value: String?): PeopleFilter {
            if (value == null) return All
            val kind = Kind.entries.firstOrNull { value.substringBefore(':') == it.name } ?: return All
            return PeopleFilter(kind, value.substringAfter(':', "").ifEmpty { null })
        }
    }
}

/** One block of the People grid. [title] is null for local kinds, which the UI names itself. */
data class PeopleSection(
    val kind: PeopleFilter.Kind,
    val key: String,
    val title: String?,
    val people: List<PersonInfo>,
)

/** Source key of on-device people; account sources use their [ProviderPeopleSection.key]. */
const val LOCAL_PEOPLE_SOURCE = "LOCAL_PEOPLE"

/** One row of the People settings sources list. [title] is null for on-device people. */
data class PeopleSource(val key: String, val title: String?, val peopleCount: Int)

/** Every source the page can show, visible or not: on-device people first, then accounts. */
fun peopleSources(state: PeopleListUiState): List<PeopleSource> = buildList {
    add(
        PeopleSource(
            LOCAL_PEOPLE_SOURCE,
            null,
            state.namedPeople.size + state.newPeople.size + state.hiddenPeopleCount
        )
    )
    state.providerSections.forEach { add(PeopleSource(it.key, it.title, it.people.size)) }
}

/**
 * The page with the sources in [hiddenSources] taken out. Turning off on-device people also drops
 * their hidden people and merge suggestions, which only exist for on-device people.
 */
fun PeopleListUiState.withoutSources(hiddenSources: Set<String>): PeopleListUiState {
    if (hiddenSources.isEmpty()) return this
    val localHidden = LOCAL_PEOPLE_SOURCE in hiddenSources
    return copy(
        namedPeople = if (localHidden) emptyList() else namedPeople,
        newPeople = if (localHidden) emptyList() else newPeople,
        hiddenPeople = if (localHidden) emptyList() else hiddenPeople,
        hiddenPeopleCount = if (localHidden) 0 else hiddenPeopleCount,
        mergeSuggestions = if (localHidden) emptyList() else mergeSuggestions,
        providerSections = providerSections.filter { it.key !in hiddenSources },
    )
}

/**
 * The chips worth showing: All, then each group that has people. Hidden appears whenever hidden
 * people exist, since the chip is how they are reached.
 */
fun availablePeopleFilters(state: PeopleListUiState): List<PeopleFilter> = buildList {
    add(PeopleFilter.All)
    if (state.namedPeople.isNotEmpty()) add(PeopleFilter(PeopleFilter.Kind.NAMED))
    if (state.newPeople.isNotEmpty()) add(PeopleFilter(PeopleFilter.Kind.NEW))
    if (state.hiddenPeopleCount > 0) add(PeopleFilter(PeopleFilter.Kind.HIDDEN))
    state.providerSections.filter { it.people.isNotEmpty() }
        .forEach { add(PeopleFilter(PeopleFilter.Kind.PROVIDER, it.key)) }
}

/** A selected chip that no longer has people falls back to All. */
fun resolvePeopleFilter(selected: PeopleFilter, available: List<PeopleFilter>): PeopleFilter =
    if (selected in available) selected else PeopleFilter.All

/** The sections the grid shows under [filter], in page order. */
fun peopleSections(state: PeopleListUiState, filter: PeopleFilter): List<PeopleSection> {
    val all = buildList {
        add(PeopleSection(PeopleFilter.Kind.NAMED, "named", null, state.namedPeople))
        add(PeopleSection(PeopleFilter.Kind.NEW, "new", null, state.newPeople))
        add(PeopleSection(PeopleFilter.Kind.HIDDEN, "hidden", null, state.hiddenPeople))
        state.providerSections.forEach {
            add(PeopleSection(PeopleFilter.Kind.PROVIDER, it.key, it.title, it.people))
        }
    }.filter { it.people.isNotEmpty() }
    return when (filter.kind) {
        PeopleFilter.Kind.ALL -> all
        PeopleFilter.Kind.PROVIDER -> all.filter { it.kind == PeopleFilter.Kind.PROVIDER && it.key == filter.key }
        else -> all.filter { it.kind == filter.kind }
    }
}
