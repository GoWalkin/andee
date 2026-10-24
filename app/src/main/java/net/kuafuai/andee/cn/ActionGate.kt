package net.kuafuai.andee.cn

import android.content.Context
import android.util.Log
import net.kuafuai.andee.config.VoiceConfig
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * The Android half of [ActionGuard]: the state it decides against, and the one
 * call every tap makes before a gesture is dispatched. (GoWalkin fork.)
 *
 * Called from `ScreenController.tap` / `longPress` — the chokepoint every eid
 * tap and every grid tap funnels through — and from `submitInput`, because
 * pressing the keyboard's Send is a send exactly like tapping 发送 and would
 * otherwise be the way round the guard. A refusal is an
 * [IllegalStateException] carrying a sentence for the model; it is thrown
 * before `dispatchGesture`, and `passthroughForGesture`'s `finally` restores the
 * overlay as it does for any other failure.
 *
 * State:
 *  * **The last approval** — set by the dispatcher's `ui.ask` branch when the
 *    user picks the first (affirmative) button. Memory only, single-use, two
 *    minutes: a restart forgetting a yes is the safe direction.
 *  * **Today's greetings per app, and when the next may go out** — in
 *    `cn_gate` prefs, so a service restart cannot reset the daily cap.
 *  * **Free-text sends in the last hour** (full-auto ceiling) — memory only.
 *
 * Tool calls run serially (see `LocalBrain`), so the check-then-record pair
 * cannot interleave with another tap; the lock is for the `ui.ask` callback,
 * which lands from a different thread.
 */
object ActionGate {

    private const val TAG = "Body"
    private const val PREFS = "cn_gate"
    private const val GREET_SPACING_MIN_MS = 20_000L
    private const val GREET_SPACING_MAX_MS = 45_000L

    @Volatile private var appContext: Context? = null
    private val lock = Any()
    private var approvalAt = 0L
    private val sends = ArrayDeque<Long>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The user picked the affirmative (first) button of an `ask_user` card. */
    fun noteApproval() {
        synchronized(lock) { approvalAt = System.currentTimeMillis() }
    }

    /**
     * Throws when a tap on an element labelled [labels] (everything under the
     * finger, smallest first) in [pkg] is not allowed; records what an allowed
     * one costs. Labels that classify as nothing return at once.
     */
    fun check(pkg: String?, labels: List<String>) {
        val (kind, label) = ActionGuard.classifyAny(pkg, labels) ?: return
        decide(kind, pkg, label)
    }

    /** `submit_input` in a guarded chat app is a send by another name. */
    fun checkSubmit(pkg: String?) {
        val p = pkg ?: return
        if (ActionGuard.SEND_PACKAGES.none { p.startsWith(it) }) return
        decide(ActionGuard.Kind.SEND, pkg, "<keyboard send>")
    }

    private fun decide(kind: ActionGuard.Kind, pkg: String?, label: String?) {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val greetKey = "greets_${day}_${pkg.orEmpty()}"
        synchronized(lock) {
            while (sends.isNotEmpty() && now - sends.peekFirst()!! > 3_600_000L) sends.pollFirst()
            val state = ActionGuard.State(
                approvalAt = approvalAt,
                greetsToday = prefs.getInt(greetKey, 0),
                greetCap = VoiceConfig.greetCap(ctx),
                nextGreetAt = prefs.getLong("next_greet_at", 0L),
                sendsThisHour = sends.size,
            )
            val autonomy = ActionGuard.Autonomy.parse(VoiceConfig.autonomy(ctx))
            when (val d = ActionGuard.decide(kind, autonomy, state, now)) {
                is ActionGuard.Decision.Refuse -> {
                    Log.i(TAG, "guard refused $kind '$label' in $pkg (autonomy=$autonomy)")
                    throw IllegalStateException("Refused by the device ($kind): ${d.reason}")
                }
                is ActionGuard.Decision.Allow -> {
                    if (d.consumesApproval) approvalAt = 0L
                    if (d.countsGreet) {
                        val spacing = Random.nextLong(GREET_SPACING_MIN_MS, GREET_SPACING_MAX_MS)
                        val edit = prefs.edit()
                            .putInt(greetKey, state.greetsToday + 1)
                            .putLong("next_greet_at", now + spacing)
                        // Yesterday's counters are dead weight; drop them as today's first is written.
                        prefs.all.keys.filter { it.startsWith("greets_") && !it.startsWith("greets_${day}_") }
                            .forEach { edit.remove(it) }
                        edit.apply()
                    }
                    if (d.countsSend) sends.addLast(now)
                    Log.i(TAG, "guard allowed $kind '$label' in $pkg (autonomy=$autonomy)")
                }
            }
        }
    }
}
