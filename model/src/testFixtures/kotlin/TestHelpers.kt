package ru.yole.etymograph

fun Language.word(text: String, gloss: String? = null, pos: String? = null) = Word(-1, text, this, gloss, pos = pos)
