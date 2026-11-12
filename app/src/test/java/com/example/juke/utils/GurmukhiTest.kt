package com.example.juke.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class GurmukhiTest {
    private fun t(g: String) = Gurmukhi.toLatin(g)

    @Test fun thinkingOfYouReadsLikeWrittenPunjabi() {
        // AP Dhillon, "Thinking of You" (LRCLIB 27547365, lines 1-12).
        val song = """
            ਸੋਚਾਂ ਤੇਰੇ ਬਾਰੇ ਬੈਠਾ ਪਾਣੀ ਦੇ ਕਿਨਾਰੇ
            ਮੈਥੋਂ ਮੀਲਾਂ ਦੂਰ ਤੂੰ ਏਂ, ਤਾਂ ਵੀ ਖ਼ਾਬਾਂ ਵਿੱਚ ਕੋਲ਼
            ਪਾਣੀ ਦੀਆਂ ਛੱਲਾਂ ਵਿੱਚੋਂ, ਲੋਕਾਂ ਦੀਆਂ ਗੱਲਾਂ ਵਿੱਚੋਂ
            ਸੁਣਦਾ ਨਾ ਕੁੱਝ, ਸੁਣਦੇ ਨੇ ਤੇਰੇ ਬੋਲ
            ਤੇਰੇ ਬਿਨਾਂ ਏ ਦੁਨੀਆਂ ਅਧੂਰੀ ਮੇਰੀ
            ਤੂੰ ਮਿਲ਼ੇਂ, ਤਲਾਸ਼ ਹੁੰਦੀ ਪੂਰੀ ਮੇਰੀ
            ਅੰਬਰ ਦੇ ਤਾਰਿਆਂ ਕੋਲ਼ੋਂ, ਜੋ ਮਿਲਦਾ ਸਾਰਿਆਂ ਕੋਲ਼ੋਂ
            ਪੁੱਛਦਾ ਮੈਂ ਪਤਾ ਤੇਰਾ, ਕੀ ਏ ਮਜਬੂਰੀ ਮੇਰੀ?
            ਭੁੱਲਦਾ ਨਾ ਮੈਨੂੰ ਜੋ ਦੇਖਿਆ ਸੀ ਤੈਨੂੰ
            ਤੂੰ ਨਾ ਮਿਲ਼ੇਂ ਤਾਂ ਹੋ ਜਾਣਾ ਆਂ ਮੈਂ ਯਾਦਾਂ ਨਾ' ਖ਼ਫ਼ਾ (huh-huh)
            ਕਿੱਦਾਂ ਸਮਝਾਵਾਂ ਮੈਂ? ਦਿਲ ਤੋਂ ਤੈਨੂੰ ਚਾਹਵਾਂ ਮੈਂ
            ਜੀਣੇ ਦੀ ਬਸ ਮੇਰੀ ਤੂੰ ਹੀ ਏਂ ਵਜ੍ਹਾ
        """.trimIndent().lines().map(::t)
        println(song.joinToString("\n"))
        assertEquals("sochan tere baare baitha paani de kinare", song[0])
        assertEquals("maithon meelan door tu en, taan vi khaaban vich kol", song[1])
        assertEquals("paani diyan chhallan vichon, lokan diyan gallan vichon", song[2])
        assertEquals("sunda na kujh, sunde ne tere bol", song[3])
        assertEquals("tere binan e duniyan adhoori meri", song[4])
        assertEquals("tu milen, talash hundi poori meri", song[5])
        assertEquals("ambar de taariyan kolon, jo milda saariyan kolon", song[6])
        assertEquals("puchhda main pata tera, ki e majboori meri?", song[7])
        assertEquals("bhulda na mainu jo dekhiya si tainu", song[8])
        assertEquals("kiddan samjhavan main? dil ton tainu chaahvan main", song[10])
        assertEquals("jeene di bas meri tu hi en vajha", song[11])
    }

    @Test fun addakTippiAndMixedText() {
        assertEquals("gaddi challi pind nu", t("ਗੱਡੀ ਚੱਲੀ ਪਿੰਡ ਨੂੰ"))
        assertEquals("gall (yeah) 2024.", t("ਗੱਲ (yeah) ੨੦੨੪।"))
    }
}
