package net.kuafuai.andee.cn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAliasesTest {

    @Test
    fun englishPinyinAndChineseAllResolve() {
        assertEquals(listOf("com.sdu.didi.psnger"), AppAliases.candidates("DiDi"))
        assertEquals(listOf("com.sdu.didi.psnger"), AppAliases.candidates("Didi Chuxing"))
        assertEquals(listOf("com.sdu.didi.psnger"), AppAliases.candidates("滴滴出行"))
        assertEquals(listOf("com.hpbr.bosszhipin"), AppAliases.candidates("BOSS直聘"))
        assertEquals(listOf("com.hpbr.bosszhipin"), AppAliases.candidates("Boss Zhipin"))
        assertEquals(listOf("com.eg.android.AlipayGphone"), AppAliases.candidates("zhifubao"))
        assertEquals(listOf("com.tencent.mm"), AppAliases.candidates("WeChat"))
        assertEquals(listOf("com.autonavi.minimap"), AppAliases.candidates("Gaode Map"))
        assertEquals(listOf("ctrip.android.view"), AppAliases.candidates("Trip.com"))
        assertEquals(listOf("me.ele"), AppAliases.candidates("Ele.me"))
    }

    @Test
    fun unknownNamesGiveNothing() {
        assertTrue(AppAliases.candidates("Spotify").isEmpty())
        assertTrue(AppAliases.candidates("   ").isEmpty())
    }
}
