package com.example.juke.utils

/**
 * Gurmukhi (Punjabi) to Latin the way Punjabi lyrics are usually written: "Sochan tere baare baitha
 * paani de kinare", not Google's ISO 15919 "Sōcāṁ tērē bārē baiṭhā pāṇī dē kinārē". Google also
 * drops the addak (ਗੱਡੀ "gaḍī", not "gaddi") and the tippi (ਪਿੰਡ "piḍa", not "pind"); the script
 * is phonetic, so it is transliterated here instead, offline.
 *
 * Rules: the inherent "a" is dropped at the end of a word and between two voiced syllables
 * (ਸੁਣਦਾ "sunda"); a long ā is "aa" only in the first of several syllables (ਬਾਰੇ "baare",
 * ਕਿਨਾਰੇ "kinare"); ī/ū are "ee"/"oo" inside a word and "i"/"u" at its end; a vowel after i
 * takes a "y" (ਦੀਆਂ "diyan"); nasals are "n" ("m" before p/b/bh/m) and silent after a final i/u
 * (ਨੂੰ "nu"); the addak doubles a single-letter consonant unless a cluster follows (ਭੁੱਲਦਾ "bhulda").
 */
internal object Gurmukhi {
    private val CONSONANTS = mapOf(
        'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "ng",
        'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "nj",
        'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n",
        'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
        'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m",
        'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", 'ੜ' to "r", 'ਸ' to "s", 'ਹ' to "h",
        '\u0A36' to "sh", '\u0A59' to "kh", '\u0A5A' to "gh", '\u0A5B' to "z", '\u0A5E' to "f", '\u0A33' to "l", // ਸ਼ ਖ਼ ਗ਼ ਜ਼ ਫ਼ ਲ਼
    )
    private val NUKTA = mapOf("s" to "sh", "g" to "gh", "j" to "z", "ph" to "f")

    /** Vowel signs and independent vowels, by their long/short sound. */
    private val SIGNS = mapOf('ਾ' to 'A', 'ਿ' to 'i', 'ੀ' to 'I', 'ੁ' to 'u', 'ੂ' to 'U', 'ੇ' to 'e', 'ੈ' to 'E', 'ੋ' to 'o', 'ੌ' to 'O')
    private val VOWELS = mapOf('ਅ' to 'a', 'ਆ' to 'A', 'ਇ' to 'i', 'ਈ' to 'I', 'ਉ' to 'u', 'ਊ' to 'U', 'ਏ' to 'e', 'ਐ' to 'E', 'ਓ' to 'o', 'ਔ' to 'O')
    private val CARRIERS = mapOf('ੲ' to 'e', 'ੳ' to 'u') // take a vowel sign: ੲਿ = ਇ
    private val LABIALS = setOf("p", "ph", "b", "bh", "m", "f")

    /** One syllable: a consonant ("" for a bare vowel), its vowel (null after a virama). */
    private class Syl(var cons: String, var vowel: Char?, var inherent: Boolean) {
        var nasal = false
        var doubled = false
    }

    fun isGurmukhi(ch: Char) = ch in '਀'..'੿'

    fun toLatin(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (!isGurmukhi(text[i])) { out.append(text[i]); i++; continue }
            val start = i
            while (i < text.length && isGurmukhi(text[i])) i++
            out.append(word(text.substring(start, i)))
        }
        return out.toString().replace('।', '.').replace("॥", ".")
    }

    private fun word(word: String): String {
        val syls = ArrayList<Syl>()
        val digits = StringBuilder()
        var addak = false
        for (ch in word) {
            val last = syls.lastOrNull()
            when {
                ch in '੦'..'੯' -> digits.append('0' + (ch - '੦'))
                ch in CONSONANTS -> syls += Syl(CONSONANTS.getValue(ch), 'a', true).also { it.doubled = addak; addak = false }
                ch == '਼' && last != null -> last.cons = NUKTA[last.cons] ?: last.cons
                ch in SIGNS -> if (last != null && last.inherent) { last.vowel = SIGNS[ch]; last.inherent = false }
                    else syls += Syl("", SIGNS[ch], false)
                ch in VOWELS -> syls += Syl("", VOWELS[ch], false)
                ch in CARRIERS -> syls += Syl("", CARRIERS[ch], true)
                ch == '੍' && last != null -> { last.vowel = null; last.inherent = false }
                ch == 'ੵ' && last != null -> last.cons += "y"
                (ch == 'ੰ' || ch == 'ਂ') && last != null -> last.nasal = true
                ch == 'ੱ' -> addak = true
            }
        }
        if (syls.isEmpty()) return digits.toString()
        dropSchwas(syls)
        val vowels = syls.count { it.vowel != null }
        var seenVowel = false
        val sb = StringBuilder(digits)
        syls.forEachIndexed { index, s ->
            val next = syls.getOrNull(index + 1)
            val final = index == syls.lastIndex
            // ਦੀਆਂ "diyan", ਦੇਖਿਆ "dekhiya": a bare vowel after i takes a y.
            if (s.cons.isEmpty() && index > 0 && (syls[index - 1].vowel == 'i' || syls[index - 1].vowel == 'I')) sb.append('y')
            if (s.doubled && s.cons.length == 1 && s.vowel != null) sb.append(s.cons)
            else if (s.doubled && s.cons.length == 1 && final) sb.append(s.cons) // ਗੱਲ "gall"
            sb.append(s.cons)
            val v = s.vowel
            if (v != null) {
                val beforeVowel = next != null && next.cons.isEmpty()
                sb.append(
                    when (v) {
                        'A' -> if (!seenVowel && (vowels > 1 || s.nasal || s.cons.isEmpty())) "aa" else "a"
                        'I' -> if (final || beforeVowel) "i" else "ee"
                        'U' -> if (final || beforeVowel) "u" else "oo"
                        'E' -> "ai"
                        'O' -> "au"
                        else -> v.toString()
                    }
                )
                seenVowel = true
            }
            if (s.nasal && !(final && (v == 'I' || v == 'U' || v == 'i' || v == 'u'))) {
                sb.append(if (next != null && next.cons in LABIALS) "m" else "n")
            }
        }
        return sb.toString()
    }

    /** Hindi/Punjabi schwa syncope: drop a final inherent a, and a medial one between voiced syllables. */
    private fun dropSchwas(syls: List<Syl>) {
        var i = syls.lastIndex
        while (i >= 1) {
            val s = syls[i]
            val droppable = s.inherent && s.cons.isNotEmpty() && !s.nasal
            val final = i == syls.lastIndex
            if (droppable && (final || (syls[i - 1].vowel != null && syls[i + 1].vowel != null))) {
                s.vowel = null; s.inherent = false
                i -= 2 // never drop two in a row
            } else i--
        }
    }
}
