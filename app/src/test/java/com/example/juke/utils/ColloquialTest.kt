package com.example.juke.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ColloquialTest {
    @Test fun urduKnownWordsReplaceGooglesVowelGuesses() {
        // Google's answers, 2026-11-12
        assertEquals("tum hi ho, ab tum hi ho, zindagi ab tum hi ho, dil diyan gallan",
            Colloquial.polish("تم ہی ہو، اب تم ہی ہو، زندگی اب تم ہی ہو، دل دیاں گلاں",
                "tam hi ho, ab tam hi ho, zandgi ab tam hi ho, dal diyan gulan"))
        assertEquals("dil diyan gallan, main tainu pyaar karda haan",
            Colloquial.polish("دل دیاں گلاں، میں تینوں پیار کردا ہاں", "dal diyan gulan, min tinon piyar karda han"))
    }

    @Test fun hindiSlipsAndCapitals() {
        assertEquals("Zindagi, khwab, fursat, mohabbat", Colloquial.polish("ज़िंदगी, ख़्वाब, फ़ुरसत, मोहब्बत", "Zindgi, khwab, fursat, mohabet"))
    }

    @Test fun answerWithDifferentWordCountIsLeftAlone() {
        assertEquals("tam hi", Colloquial.polish("تم ہی ہو", "tam hi"))
    }

    @Test fun isoMarksAreFolded() {
        assertEquals("Naanu ninnannu preetisuttene", Colloquial.polish("ನಾನು ನಿನ್ನನ್ನು ಪ್ರೀತಿಸುತ್ತೇನೆ", "Nānu ninnannu prītisuttēne"))
        assertEquals("suno diyan gallan", Colloquial.foldIso("suṇō dī'āṁ gallāṁ"))
    }

    @Test fun urduTakesItsShortVowelsFromHindi() {
        // Google's Urdu and Hindi answers for Ajnabi (Atif Aslam), 2026-11-12
        val hindi = listOf("main", "dukan", "se", "ghar", "chala", "gaya")
        assertEquals("main ghar se jo nikla dukan se",
            Colloquial.polish("میں گھر سے جو نکلا دکان سے", "min ghar se jo nikala dakan se") { _, _ -> hindi + "nikla" })
        assertEquals("mumkin", Colloquial.revowel("mamkan", listOf("sambhav", "mumkin")))
        assertEquals("ummidon", Colloquial.revowel("umidon", listOf("ummidon")))
        assertEquals("sataya", Colloquial.revowel("staya", listOf("sataya", "gaya")))
        assertEquals("nahin", Colloquial.revowel("nihen", listOf("nahin")))
    }

    @Test fun meinIsTakenFromTheHindiOfItsLine() {
        // postposition: the Hindi word in its place is "mein"
        assertEquals("ghar mein", Colloquial.polish("گھر میں", "ghar min") { i, _ -> listOf(listOf("ghar", "mein")[i]) })
        // pronoun: the Hindi word in its place is "main"
        assertEquals("main ghar", Colloquial.polish("میں گھر", "min ghar") { i, _ -> listOf(listOf("main", "ghar")[i]) })
        // no Hindi to go by: the pronoun
        assertEquals("main", Colloquial.polish("میں", "min"))
    }

    @Test fun theEndingStaysAsUrduWroteIt() {
        assertEquals("lene", Colloquial.revowel("line", listOf("lena")))
        assertEquals("meri", Colloquial.revowel("meri", listOf("mera")))
        assertEquals("thi", Colloquial.revowel("thi", listOf("tha")))
        assertEquals("milna", Colloquial.revowel("malnaa", listOf("milna")))
    }

    @Test fun aCandidateThatIsAnotherWordIsRefused() {
        assertEquals(null, Colloquial.revowel("kasi", listOf("koi")))       // other consonants
        assertEquals(null, Colloquial.revowel("kam", listOf("kami")))       // another form
        assertEquals(null, Colloquial.revowel("ajnabi", listOf("ajanb")))
        assertEquals(null, Colloquial.revowel("hen", listOf("han")))        // contradicts a written vowel
        assertEquals(null, Colloquial.revowel("man", listOf("mein")))       // a long vowel Urdu would have written
        assertEquals(null, Colloquial.revowel("thakana", listOf("thkana"))) // no word starts "thk"
    }
}
