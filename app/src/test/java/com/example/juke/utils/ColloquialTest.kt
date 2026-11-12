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
}
