/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleSourcesTest {
    private fun person(id: String, type: ProviderType = ProviderType.LOCAL_PEOPLE) =
        PersonInfo(id = id, name = id, providerType = type, serverConfigId = 1L)

    private val state = PeopleListUiState(
        namedPeople = listOf(person("a")),
        hiddenPeopleCount = 1,
        providerSections = listOf(
            ProviderPeopleSection("IMMICH_1", "Immich", listOf(person("b", ProviderType.IMMICH)))
        ),
        isLoading = false,
    )

    @Test
    fun sourcesListOnDevicePeopleFirstWithHiddenPeopleCounted() {
        assertEquals(
            listOf(PeopleSource(LOCAL_PEOPLE_SOURCE, null, 2), PeopleSource("IMMICH_1", "Immich", 1)),
            peopleSources(state)
        )
    }

    @Test
    fun hidingOnDevicePeopleDropsTheirHiddenCountButKeepsAccounts() {
        val visible = state.withoutSources(setOf(LOCAL_PEOPLE_SOURCE))
        assertTrue(visible.namedPeople.isEmpty())
        assertEquals(0, visible.hiddenPeopleCount)
        assertEquals(1, visible.providerSections.size)
    }

    @Test
    fun hidingAnAccountRemovesOnlyThatSection() {
        val visible = state.withoutSources(setOf("IMMICH_1"))
        assertEquals(1, visible.namedPeople.size)
        assertTrue(visible.providerSections.isEmpty())
    }
}
