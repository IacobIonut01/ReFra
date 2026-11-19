/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


// Albums, people, cloud preferences, and device UI write several record kinds into one section
// file, so each line carries a serial name discriminator.
@Serializable
sealed class PeopleRecord {
    @Serializable @kotlinx.serialization.SerialName("person")
    data class Person(val value: PersonRecord) : PeopleRecord()

    @Serializable @kotlinx.serialization.SerialName("exclusion")
    data class Exclusion(val value: FaceExclusionRecord) : PeopleRecord()

    @Serializable @kotlinx.serialization.SerialName("link")
    data class Link(val value: FaceLinkRecord) : PeopleRecord()

    @Serializable @kotlinx.serialization.SerialName("suppression")
    data class Suppression(val value: FaceSuppressionRecord) : PeopleRecord()
}
