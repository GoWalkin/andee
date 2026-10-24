package net.kuafuai.andee.cn

import net.kuafuai.andee.cn.ActionGuard.Autonomy
import net.kuafuai.andee.cn.ActionGuard.Decision
import net.kuafuai.andee.cn.ActionGuard.Kind
import net.kuafuai.andee.cn.ActionGuard.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGuardTest {

    private val didi = "com.sdu.didi.psnger"
    private val boss = ActionGuard.BOSS
    private val wechat = "com.tencent.mm"
    private val alipay = "com.eg.android.AlipayGphone"

    // ---- classification ----------------------------------------------------

    @Test
    fun payButtonsAnywhere() {
        assertEquals(Kind.PAY, ActionGuard.classify(alipay, "立即支付"))
        assertEquals(Kind.PAY, ActionGuard.classify(didi, "立即支付 ¥23.50"))
        assertEquals(Kind.PAY, ActionGuard.classify(wechat, "确认支付"))
        assertEquals(Kind.PAY, ActionGuard.classify("com.taobao.taobao", "去付款(¥99)"))
    }

    @Test
    fun wordsThatOnlyStartLikePayAreNot() {
        assertNull(ActionGuard.classify(alipay, "支付宝"))
        assertNull(ActionGuard.classify(didi, "支付方式"))
        assertNull(ActionGuard.classify(alipay, "Payment methods"))
    }

    @Test
    fun rideOrders() {
        assertEquals(Kind.ORDER, ActionGuard.classify(didi, "确认呼叫"))
        assertEquals(Kind.ORDER, ActionGuard.classify(didi, "确认呼叫(快车)"))
        assertEquals(Kind.ORDER, ActionGuard.classify(didi, "呼叫快车"))
        assertEquals(Kind.ORDER, ActionGuard.classify("com.autonavi.minimap", "呼叫"))
        assertEquals(Kind.ORDER, ActionGuard.classify(wechat, "立即叫车"))
        // A short word is only an order inside a ride app.
        assertNull(ActionGuard.classify(wechat, "呼叫"))
        // Confirming the pickup point is not ordering.
        assertNull(ActionGuard.classify(didi, "确认上车点"))
        assertNull(ActionGuard.classify(didi, "呼叫司机"))
    }

    @Test
    fun shoppingOrdersAnywhere() {
        assertEquals(Kind.ORDER, ActionGuard.classify("com.sankuai.meituan", "提交订单"))
        assertEquals(Kind.ORDER, ActionGuard.classify("com.taobao.taobao", "立即购买"))
    }

    @Test
    fun bossButtons() {
        assertEquals(Kind.GREET, ActionGuard.classify(boss, "立即沟通"))
        assertEquals(Kind.GREET, ActionGuard.classify(boss, "投递简历"))
        assertEquals(Kind.ROUTINE, ActionGuard.classify(boss, "发送附件简历"))
        assertEquals(Kind.SEND, ActionGuard.classify(boss, "发送"))
        assertEquals(Kind.SEND, ActionGuard.classify(boss, "交换微信"))
        assertEquals(Kind.SEND, ActionGuard.classify(boss, "同意"))
        assertNull(ActionGuard.classify(boss, "继续沟通"))
        assertNull(ActionGuard.classify(boss, "Android开发工程师"))
        // The same words outside BOSS are not greetings.
        assertNull(ActionGuard.classify(wechat, "立即沟通"))
    }

    @Test
    fun sendOnlyGuardedInListedApps() {
        assertEquals(Kind.SEND, ActionGuard.classify(didi, "发送"))
        assertNull(ActionGuard.classify(wechat, "发送"))
    }

    @Test
    fun resourceNamesAndBlanksAreNothing() {
        assertNull(ActionGuard.classify(boss, "#btn_chat"))
        assertNull(ActionGuard.classify(boss, ""))
        assertNull(ActionGuard.classify(boss, null))
    }

    // ---- decisions ---------------------------------------------------------

    private val now = 1_000_000_000L

    @Test
    fun payIsAlwaysRefused() {
        for (a in Autonomy.values()) {
            assertTrue(ActionGuard.decide(Kind.PAY, a, State(approvalAt = now), now) is Decision.Refuse)
        }
    }

    @Test
    fun orderNeedsFreshApprovalAtEveryLevel() {
        for (a in Autonomy.values()) {
            assertTrue(ActionGuard.decide(Kind.ORDER, a, State(), now) is Decision.Refuse)
            assertEquals(
                Decision.Allow(consumesApproval = true),
                ActionGuard.decide(Kind.ORDER, a, State(approvalAt = now - 5_000), now),
            )
            // Expired.
            assertTrue(
                ActionGuard.decide(Kind.ORDER, a, State(approvalAt = now - ActionGuard.APPROVAL_TTL_MS - 1), now)
                    is Decision.Refuse,
            )
        }
    }

    @Test
    fun greetFollowsAutonomyCapAndPacing() {
        assertTrue(ActionGuard.decide(Kind.GREET, Autonomy.ASSIST, State(), now) is Decision.Refuse)
        assertEquals(Decision.Allow(countsGreet = true), ActionGuard.decide(Kind.GREET, Autonomy.SEMI, State(), now))
        assertEquals(Decision.Allow(countsGreet = true), ActionGuard.decide(Kind.GREET, Autonomy.FULL, State(), now))
        assertTrue(
            ActionGuard.decide(Kind.GREET, Autonomy.FULL, State(greetsToday = 3, greetCap = 3), now) is Decision.Refuse,
        )
        val tooSoon = ActionGuard.decide(Kind.GREET, Autonomy.SEMI, State(nextGreetAt = now + 12_000), now)
        assertTrue(tooSoon is Decision.Refuse)
        assertTrue((tooSoon as Decision.Refuse).reason.contains("12 seconds"))
    }

    @Test
    fun routineIsAllowedUnlessAssist() {
        assertTrue(ActionGuard.decide(Kind.ROUTINE, Autonomy.ASSIST, State(), now) is Decision.Refuse)
        assertEquals(Decision.Allow(), ActionGuard.decide(Kind.ROUTINE, Autonomy.SEMI, State(), now))
        assertEquals(Decision.Allow(), ActionGuard.decide(Kind.ROUTINE, Autonomy.FULL, State(), now))
    }

    @Test
    fun sendPerAutonomy() {
        assertTrue(ActionGuard.decide(Kind.SEND, Autonomy.ASSIST, State(approvalAt = now), now) is Decision.Refuse)
        assertTrue(ActionGuard.decide(Kind.SEND, Autonomy.SEMI, State(), now) is Decision.Refuse)
        assertEquals(
            Decision.Allow(consumesApproval = true),
            ActionGuard.decide(Kind.SEND, Autonomy.SEMI, State(approvalAt = now - 1_000), now),
        )
        assertEquals(Decision.Allow(countsSend = true), ActionGuard.decide(Kind.SEND, Autonomy.FULL, State(), now))
        assertTrue(
            ActionGuard.decide(Kind.SEND, Autonomy.FULL, State(sendsThisHour = ActionGuard.FULL_SENDS_PER_HOUR), now)
                is Decision.Refuse,
        )
    }

    @Test
    fun autonomyParsesFailClosedToSemi() {
        assertEquals(Autonomy.FULL, Autonomy.parse("full"))
        assertEquals(Autonomy.ASSIST, Autonomy.parse(" Assist "))
        assertEquals(Autonomy.SEMI, Autonomy.parse(null))
        assertEquals(Autonomy.SEMI, Autonomy.parse("fully"))
    }
}
