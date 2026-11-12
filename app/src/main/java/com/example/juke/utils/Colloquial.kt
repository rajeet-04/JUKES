package com.example.juke.utils

/**
 * Fixes for Google's romanizations so lyrics read the way people write them in Latin.
 *
 * - Urdu (and Punjabi in Shahmukhi) leaves out short vowels, so Google guesses them: تم "tam",
 *   دل "dal", زندگی "zandgi", تینوں "tinon". Common lyric words are looked up by their written
 *   form instead. No spelling rule can recover the vowels; the dictionary is the fix.
 * - A few Hindi words come back wrong (मोहब्बत "mohabet", ज़िंदगी "zindgi").
 * - Some answers are ISO 15919 ("Nānu prītisuttēne" for Kannada); the marks are folded to plain
 *   letters (ā "aa", final ā "a", ī "ee"/"i", ṇ "n", ś "sh", c "ch").
 *
 * Gurmukhi never comes here: [Gurmukhi] transliterates it on the device.
 */
internal object Colloquial {
    private val WORDS: Map<String, String> = buildMap {
        // Urdu / Shahmukhi
        """
        تم tum|تو tu|تُو tu|میں main|میرا mera|میری meri|میرے mere|تیرا tera|تیری teri|تیرے tere|ہم hum|ہمارا hamara|ہماری hamari
        دل dil|دلوں dilon|زندگی zindagi|پیار pyaar|محبت mohabbat|عشق ishq|یار yaar|ہے hai|ہیں hain|ہو ho|ہوں hoon|تھا tha|تھی thi
        کی ki|کا ka|کے ke|کو ko|سے se|نے ne|بھی bhi|نہیں nahin|نہ na|جب jab|تب tab|اب ab|کب kab|کیوں kyun|کیا kya|کیسے kaise
        جان jaan|جاناں jaanan|صنم sanam|دنیا duniya|رات raat|دن din|خواب khwaab|یاد yaad|یادیں yaadein|آنکھیں aankhein|آنکھوں aankhon
        دھڑکن dhadkan|سانس saans|ساتھ saath|بنا bina|ملے mile|ملا mila|سب sab|کچھ kuch|پھر phir|اور aur|لیکن lekin|مگر magar|ہی hi
        یہ yeh|وہ woh|گلاں gallan|تینوں tainu|مینوں mainu|نال naal|وچ vich|دیاں diyan|دی di|دا da|دے de|کردا karda|کردی kardi
        ہاں haan|سوہنا sohna|سجنا sajna|ماہی mahi|رب rabb|اللہ Allah|خدا khuda|نور noor|تنہا tanha|تنہائی tanhai|غم gham|درد dard
        دعا dua|چاند chaand|آسمان aasmaan|زمین zameen|پانی paani|ہوا hawa|بارش baarish|آج aaj|ہمیشہ hamesha|کبھی kabhi|بات baat
        باتیں baatein|دور door|پاس paas|قریب qareeb|رہا raha|رہی rahi|گیا gaya|گئی gayi|سوچ soch|تجھے tujhe|مجھے mujhe|تیرے tere
        """.trimIndent().split('|', '\n').forEach { entry ->
            val (word, latin) = entry.trim().split(' ')
            put(nfc(word), latin)
        }
        // Hindi words Google gets wrong
        listOf("मोहब्बत" to "mohabbat", "ज़िंदगी" to "zindagi", "जिंदगी" to "zindagi", "ज़िन्दगी" to "zindagi", "जिन्दगी" to "zindagi")
            .forEach { (word, latin) -> put(nfc(word), latin) }
    }
    /** ज़ arrives as one code point or as ज + nukta; NFC makes them the same key. */
    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
    private val PUNCT = Regex("""^([\p{P}\p{S}]*)(.*?)([\p{P}\p{S}]*)$""")
    private val ISO = Regex("[āīūēōṁṃṅñṇṭḍḷḻṛṟśṣḥẖḵĀĪŪĒŌŚṢ]")

    /** [romanized] (Google's answer for [source]) with known words replaced and ISO marks folded. */
    fun polish(source: String, romanized: String): String {
        val src = source.trim().split(Regex("\\s+"))
        val out = romanized.trim().split(Regex("\\s+"))
        // Only a word-for-word answer can be corrected word by word.
        val fixed = if (src.size == out.size) src.zip(out).joinToString(" ") { (s, o) -> word(s, o) } else romanized.trim()
        return if (ISO.containsMatchIn(fixed)) foldIso(fixed) else fixed
    }

    private fun word(source: String, romanized: String): String {
        val bare = nfc(source.trim { it.category.code[0] == 'P' || it.category.code[0] == 'S' })
        val latin = WORDS[bare] ?: return romanized
        val (lead, body, trail) = PUNCT.find(romanized)!!.destructured
        val cased = if (body.firstOrNull()?.isUpperCase() == true) latin.replaceFirstChar { it.uppercase() } else latin
        return lead + cased + trail
    }

    /** ISO 15919 to plain letters: long vowels doubled inside a word, short at its end. */
    fun foldIso(text: String): String = text.split(' ').joinToString(" ") { w ->
        val sb = StringBuilder()
        for ((i, ch) in w.withIndex()) {
            // a trailing nasal ("āṁ") or a following apostrophe ("dī'āṁ") still ends the vowel short
            val end = w.drop(i + 1).none { it.isLetter() && it !in "ṁṃ" } || w.getOrNull(i + 1) == '\''
            val upper = ch.isUpperCase()
            val s = when (ch.lowercaseChar()) {
                'ā' -> if (end) "a" else "aa"
                'ī' -> if (end) "i" else "ee"
                'ū' -> if (end) "u" else "oo"
                'ē' -> "e"
                'ō' -> "o"
                'ṁ', 'ṃ', 'ṅ', 'ñ', 'ṇ' -> "n"
                'ṭ' -> "t"
                'ḍ' -> "d"
                'ḷ', 'ḻ' -> "l"
                'ṛ', 'ṟ' -> "r"
                'ś', 'ṣ' -> "sh"
                'ḥ', 'ẖ' -> "h"
                'ḵ' -> "k"
                'c' -> if (w.getOrNull(i + 1) == 'h') "c" else "ch"
                '\'' -> if (i > 0 && w[i - 1] in "iīeē") "y" else ""
                else -> ch.toString()
            }
            sb.append(if (upper) s.replaceFirstChar { it.uppercase() } else s)
        }
        sb.toString()
    }
}
