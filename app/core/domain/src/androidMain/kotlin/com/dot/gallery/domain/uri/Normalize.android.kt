package com.dot.gallery.domain.uri

import java.text.Normalizer

actual fun String.normalizeNfkc(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)

actual fun String.normalizeNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

actual fun String.normalizeNfd(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
