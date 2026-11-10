package com.dot.gallery.domain.uri


@JsFun("(s) => s.normalize('NFKC')")
private external fun jsNormalizeNfkc(s: String): String

actual fun String.normalizeNfkc(): String = jsNormalizeNfkc(this)

@JsFun("(s) => s.normalize('NFC')")
private external fun jsNormalizeNfc(s: String): String

@JsFun("(s) => s.normalize('NFD')")
private external fun jsNormalizeNfd(s: String): String

actual fun String.normalizeNfc(): String = jsNormalizeNfc(this)

actual fun String.normalizeNfd(): String = jsNormalizeNfd(this)
