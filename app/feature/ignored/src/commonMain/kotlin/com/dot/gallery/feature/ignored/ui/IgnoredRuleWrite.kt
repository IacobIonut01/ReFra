/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui

import com.dot.gallery.domain.model.IgnoredAlbum


/** What saving the setup sheet writes. [remove] is deleted before [save] is upserted. */
data class IgnoredRuleWrite(val save: IgnoredAlbum, val remove: IgnoredAlbum? = null)
