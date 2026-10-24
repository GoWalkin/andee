package net.kuafuai.andee.cn

/**
 * What a tap on a given button is allowed to do, decided by **what the button
 * says** and **which app it is in** — not by what the model was told. (GoWalkin
 * fork.)
 *
 * Upstream's README claimed "stops before the final confirmation of a purchase
 * and never enters a payment password … those rules are in the dispatcher, not
 * in a prompt". They were not: only prompt text stood between the model and
 * 立即支付. This is the dispatcher half, in the same spirit as
 * [net.kuafuai.andee.net.TurnGuards]: every rule here also has a prompt saying
 * it, and every rule is here anyway because a prompt is a request.
 *
 * Pure and platform-free so it can be tested on the JVM. The Android side
 * (`cn/ActionGate`) owns the state — the last approval, today's greeting count —
 * and asks [decide] before every tap.
 *
 * ### What it cannot see, stated rather than hidden
 *
 * It reads the label of the element under the tap. A pane that draws its own
 * pixels (a mini-program in Alipay or WeChat, a WebView canvas) hands the
 * accessibility layer no labels, so a tap there classifies as nothing and is
 * allowed; the prompt rule is the only guard on those screens. A button renamed
 * by an app update is missed the same way until the table learns the new word.
 */
object ActionGuard {

    /** In order of severity — [classifyAny] relies on it. */
    enum class Kind {
        /** Pays money now. Never tapped by the body, at any autonomy. */
        PAY,

        /** Places an order that will cost money (calling a car, submitting an order). Needs a fresh yes. */
        ORDER,

        /** Sends words or personal contact details to a third party in a guarded chat app. */
        SEND,

        /** A job application / first greeting on BOSS直聘. Daily cap and pacing. */
        GREET,

        /** A routine reply the user pre-approved by choosing semi-auto: sending the attachment résumé. */
        ROUTINE,
    }

    /**
     * The user's own choice, from the settings card; the model has no tool to
     * change it. Anything unrecognised reads as [SEMI], the default — the
     * leftmost option in the picker, per the house rule.
     */
    enum class Autonomy {
        SEMI, ASSIST, FULL;

        companion object {
            fun parse(raw: String?): Autonomy = when (raw?.trim()?.lowercase()) {
                "assist" -> ASSIST
                "full" -> FULL
                else -> SEMI
            }
        }
    }

    /** Everything [decide] needs to know about the past, owned by the caller. */
    data class State(
        /** When the user last picked the first (affirmative) button of an `ask_user` card, or 0. */
        val approvalAt: Long = 0L,
        /** Greetings sent today in the app being tapped. */
        val greetsToday: Int = 0,
        val greetCap: Int = DEFAULT_GREET_CAP,
        /** Earliest moment the next greeting may go out (randomised spacing), or 0. */
        val nextGreetAt: Long = 0L,
        /** Free-text sends in the last hour, for the full-auto ceiling. */
        val sendsThisHour: Int = 0,
    )

    sealed interface Decision {
        /**
         * @param consumesApproval the caller must clear the approval: one yes buys one tap.
         * @param countsGreet the caller must count a greeting and re-arm the spacing.
         * @param countsSend the caller must count a send toward the hourly ceiling.
         */
        data class Allow(
            val consumesApproval: Boolean = false,
            val countsGreet: Boolean = false,
            val countsSend: Boolean = false,
        ) : Decision

        /** A sentence written for the model: what this is, why not, and what to do instead. */
        data class Refuse(val reason: String) : Decision
    }

    const val BOSS = "com.hpbr.bosszhipin"
    const val DEFAULT_GREET_CAP = 30
    const val APPROVAL_TTL_MS = 120_000L
    const val FULL_SENDS_PER_HOUR = 20

    /** Ride-hailing apps, where short words like 呼叫 mean "order a car". Prefix match. */
    val RIDE_PACKAGES = listOf(
        "com.sdu.didi", "com.didi", // 滴滴出行 (com.sdu.didi.psnger) and its siblings
        "com.autonavi.minimap", // 高德地图 — hosts DiDi, Pony and others in 打车
        "com.caocaokeji", "com.t3go", "com.hello.pet", // 曹操出行, T3出行, 哈啰
        "com.baidu.BaiduMap", // 百度地图 — hosts 萝卜快跑 (Apollo Go)
        "com.pony", "ai.pony", // Pony.ai — package unverified on Android, prefix only
    )

    /** Chat apps whose free-text send is guarded by autonomy. */
    val SEND_PACKAGES = listOf(BOSS, "com.sdu.didi", "com.didi")

    fun classify(pkg: String?, label: String?): Kind? {
        val l = normalize(label ?: return null)
        if (l.isEmpty() || l.startsWith("#")) return null
        val p = pkg.orEmpty()
        if (matches(l, PAY)) return Kind.PAY
        if (matches(l, ORDER_ANY)) return Kind.ORDER
        if (RIDE_PACKAGES.any { p.startsWith(it) } && matches(l, ORDER_RIDE)) return Kind.ORDER
        if (p == BOSS) {
            if (matches(l, GREET)) return Kind.GREET
            if (matches(l, ROUTINE)) return Kind.ROUTINE
            if (matches(l, CONTACT_SHARE)) return Kind.SEND
        }
        if (SEND_PACKAGES.any { p.startsWith(it) } && matches(l, SEND)) return Kind.SEND
        return null
    }

    /**
     * The most serious kind any of [labels] classifies as, with the label that
     * earned it. The caller passes every labelled element under the finger,
     * because the one the finger is literally on is often a child (the price
     * inside a pay button) that says nothing about what the button does.
     * Severity is the order of [Kind]: paying outranks ordering outranks sending.
     */
    fun classifyAny(pkg: String?, labels: List<String>): Pair<Kind, String>? =
        labels.mapNotNull { l -> classify(pkg, l)?.let { it to l } }.minByOrNull { it.first.ordinal }

    fun decide(kind: Kind, autonomy: Autonomy, state: State, now: Long): Decision {
        val approved = state.approvalAt > 0 && now - state.approvalAt in 0..APPROVAL_TTL_MS
        return when (kind) {
            Kind.PAY -> Decision.Refuse(
                "This button pays money. Paying is always the user's own step, at every autonomy level: " +
                    "do not tap it. Stop here and tell them, in their language, what they are paying, " +
                    "how much, and which button to press.",
            )

            Kind.ORDER ->
                if (approved) Decision.Allow(consumesApproval = true)
                else Decision.Refuse(
                    "This button places an order that costs money (a ride, a purchase, a booking). " +
                        "Ask the user first with ask_user: say what it is, from where to where or which item, " +
                        "and the price, and put the yes option FIRST in buttons. Tap again only after they " +
                        "pick it; one yes covers one tap, for two minutes.",
                )

            Kind.GREET -> when {
                autonomy == Autonomy.ASSIST -> Decision.Refuse(
                    "Autonomy is set to Assist, so you do not apply or greet for the user. Tell them which " +
                        "job this is and that the button to press is the one you were about to tap.",
                )
                state.greetsToday >= state.greetCap -> Decision.Refuse(
                    "Today's application cap (${state.greetCap}) is reached. Stop applying: BOSS直聘 restricts " +
                        "accounts that greet too many recruiters. Report what you did today and continue tomorrow.",
                )
                now < state.nextGreetAt -> Decision.Refuse(
                    "Too soon after the last application: wait about ${(state.nextGreetAt - now + 999) / 1000} " +
                        "seconds. Use the time to read the next job's details, then tap again. Pacing like a " +
                        "person is what keeps the account from being flagged.",
                )
                else -> Decision.Allow(countsGreet = true)
            }

            Kind.ROUTINE ->
                if (autonomy == Autonomy.ASSIST) Decision.Refuse(
                    "Autonomy is set to Assist, so you do not send anything for the user. Tell them the " +
                        "recruiter asked for their résumé and which button sends it.",
                )
                else Decision.Allow()

            Kind.SEND -> when (autonomy) {
                Autonomy.ASSIST -> Decision.Refuse(
                    "Autonomy is set to Assist, so you do not send messages for the user. Show them the draft " +
                        "(the Chinese text and an English translation) and let them send it themselves.",
                )
                Autonomy.SEMI ->
                    if (approved) Decision.Allow(consumesApproval = true)
                    else Decision.Refuse(
                        "Autonomy is Semi-auto: a message to someone else, or sharing contact details, needs the " +
                            "user's yes first. Ask with ask_user, showing the exact Chinese text and an English " +
                            "translation, with the send option FIRST in buttons. Then tap again.",
                    )
                Autonomy.FULL ->
                    if (state.sendsThisHour >= FULL_SENDS_PER_HOUR) Decision.Refuse(
                        "More than $FULL_SENDS_PER_HOUR messages sent in the last hour. Pause and tell the user; " +
                            "a burst like this is what gets an account flagged.",
                    )
                    else Decision.Allow(countsSend = true)
            }
        }
    }

    /** Lowercase, and drop whitespace and the decoration apps wrap round a label (prices, brackets). */
    internal fun normalize(label: String): String =
        label.lowercase().replace(Regex("[\\s\\u00a0]+"), "")

    /**
     * Exact, or the label starts with the phrase and what follows is decoration
     * rather than another word — "立即支付¥23.50", "确认呼叫(快车)", "pay·¥20". The
     * character right after the phrase decides: a letter (CJK or Latin) means the
     * phrase was only the start of a different word, so 支付宝 is not 支付, 支付方式
     * is not a pay button, and "payment methods" is not "pay". A phrase merely
     * *contained* in a longer label never matches.
     */
    internal fun matches(label: String, phrases: List<String>): Boolean =
        phrases.any { ph ->
            label == ph || (label.startsWith(ph) && !Character.isLetter(label[ph.length]))
        }

    private val PAY = listOf(
        "立即支付", "确认支付", "确认付款", "去支付", "立即付款", "去付款", "支付", "付款",
        "确认交易", "免密支付", "确认转账", "立即转账", "去结算并支付",
        "paynow", "confirmpayment", "pay",
    )

    /** Order phrases strong enough to mean "order" in any app. */
    private val ORDER_ANY = listOf(
        "确认呼叫", "呼叫快车", "呼叫专车", "呼叫出租车", "立即叫车", "确认叫车", "一键叫车", "确认用车",
        "立即用车", "呼叫无人车", "呼叫自动驾驶", "确认下单", "提交订单", "立即下单", "去下单",
        "立即购买", "确认购买", "立即预订", "确认预订", "提交预约",
        "confirmride", "callacar", "requestride", "placeorder", "submitorder", "booknow",
    )

    /** Short words that mean "order a car" only inside a ride app. */
    private val ORDER_RIDE = listOf("呼叫", "叫车", "立即预约", "确认预约", "预约用车", "call")

    private val GREET = listOf("立即沟通", "打招呼", "投递简历", "立即投递", "一键投递", "我要投递", "申请职位", "chatnow", "apply")

    private val ROUTINE = listOf("发送附件简历", "发送简历", "发简历")

    /** On BOSS: handing over a phone number or WeChat id, or accepting a recruiter's request for one. */
    private val CONTACT_SHARE = listOf("交换微信", "换微信", "交换电话", "换电话", "同意")

    private val SEND = listOf("发送", "send")
}
