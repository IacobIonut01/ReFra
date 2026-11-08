/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleFilterTest {
    private fun person(id: String, name: String = "", type: ProviderType = ProviderType.LOCAL_PEOPLE) =
        PersonInfo(id = id, name = name, providerType = type, serverConfigId = 1L)

    private val state = PeopleListUiState(
        namedPeople = listOf(person("a", "Ana")),
        newPeople = listOf(person("b")),
        hiddenPeopleCount = 2,
        providerSections = listOf(
            ProviderPeopleSection("immich", "Immich", listOf(person("c", "Cam", ProviderType.IMMICH)))
        ),
        isLoading = false,
    )

    @Test
    fun hiddenChipStaysWhileHiddenPeopleExistEvenIfNotLoaded() {
        val kinds = availablePeopleFilters(state).map { it.kind }
        assertEquals(
            listOf(
                PeopleFilter.Kind.ALL, PeopleFilter.Kind.NAMED, PeopleFilter.Kind.NEW,
                PeopleFilter.Kind.HIDDEN, PeopleFilter.Kind.PROVIDER
            ),
            kinds
        )
    }

    @Test
    fun filterShowsOnlyItsGroupAndAllShowsEveryNonEmptyGroup() {
        assertEquals(listOf("named", "new", "immich"), peopleSections(state, PeopleFilter.All).map { it.key })
        assertEquals(
            listOf("immich"),
            peopleSections(state, PeopleFilter(PeopleFilter.Kind.PROVIDER, "immich")).map { it.key }
        )
        assertEquals(listOf("new"), peopleSections(state, PeopleFilter(PeopleFilter.Kind.NEW)).map { it.key })
    }

    @Test
    fun aChipWithoutPeopleFallsBackToAll() {
        val available = availablePeopleFilters(state.copy(newPeople = emptyList()))
        assertEquals(PeopleFilter.All, resolvePeopleFilter(PeopleFilter(PeopleFilter.Kind.NEW), available))
    }

    @Test
    fun encodedFiltersRoundTrip() {
        val provider = PeopleFilter(PeopleFilter.Kind.PROVIDER, "immich:1")
        assertEquals(provider, PeopleFilter.decode(provider.encode()))
        assertEquals(PeopleFilter.All, PeopleFilter.decode("garbage"))
    }
}
