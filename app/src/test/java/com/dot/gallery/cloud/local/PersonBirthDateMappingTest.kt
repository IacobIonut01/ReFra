/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.local

import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.PersonEntity
import com.dot.gallery.cloud.data.entity.toPersonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonBirthDateMappingTest {

    @Test
    fun storedDateSurfacesOnPersonInfo() {
        val info = person(birthDate = "2013-08-21").toPersonInfo(photoCount = 4)

        assertEquals("2013-08-21", info.birthDate)
        assertEquals(4, info.assetCount)
        assertEquals(LOCAL_PEOPLE_CONFIG_ID, info.serverConfigId)
        assertEquals(ProviderType.LOCAL_PEOPLE, info.providerType)
    }

    @Test
    fun blankDateIsUnset() {
        assertNull(person(birthDate = "").toPersonInfo(photoCount = 0).birthDate)
        assertNull(person(birthDate = "   ").toPersonInfo(photoCount = 0).birthDate)
    }

    private fun person(birthDate: String) = PersonEntity(
        id = "local_1",
        name = "Ada",
        providerType = ProviderType.LOCAL_PEOPLE,
        birthDate = birthDate
    )
}
