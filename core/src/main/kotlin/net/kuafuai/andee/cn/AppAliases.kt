package net.kuafuai.andee.cn

/**
 * English, pinyin and Chinese names for the apps people in mainland China use,
 * mapped to their package names. (GoWalkin fork.)
 *
 * `launch_app` matched only a launcher label, exactly, and labels follow the
 * phone's language: on a Chinese-language phone DiDi is 滴滴出行, so "open DiDi"
 * from someone who cannot read Chinese found nothing. This table is the
 * fallback after the exact label match, consulted by
 * `DeviceActionsController.resolveLabel`, which keeps only packages that are
 * actually installed — so a package listed here that the phone does not have
 * costs nothing, and a wrong guess cannot launch the wrong app.
 *
 * Packages are the well-known ones; anything marked unverified is a prefix
 * guess that only ever helps when it happens to be installed.
 */
object AppAliases {

    private class App(val packages: List<String>, vararg names: String) {
        val keys: Set<String> = names.map(::normalize).toSet()
    }

    private val APPS = listOf(
        App(listOf("com.tencent.mm"), "wechat", "weixin", "微信"),
        App(listOf("com.tencent.wework"), "wecom", "wechat work", "qiye weixin", "企业微信"),
        App(listOf("com.tencent.mobileqq"), "qq"),
        App(listOf("com.eg.android.AlipayGphone"), "alipay", "zhifubao", "支付宝"),
        App(listOf("com.unionpay"), "unionpay", "yunshanfu", "cloud quickpass", "云闪付"),
        App(listOf("com.sdu.didi.psnger"), "didi", "didi chuxing", "didi rider", "滴滴", "滴滴出行"),
        App(listOf("com.huaxiaozhu.rider"), "huaxiaozhu", "花小猪", "花小猪打车"),
        App(listOf("com.caocaokeji.user"), "caocao", "曹操出行"),
        App(listOf("com.jingyao.easybike"), "hello", "hellobike", "haluo", "哈啰", "哈啰出行"),
        App(listOf("com.autonavi.minimap"), "amap", "gaode", "gaode map", "高德", "高德地图"),
        App(listOf("com.baidu.BaiduMap"), "baidu map", "baidu maps", "百度地图"),
        App(listOf("com.tencent.map"), "tencent map", "tencent maps", "腾讯地图"),
        App(listOf("com.hpbr.bosszhipin"), "boss", "boss zhipin", "bosszhipin", "boss直聘", "直聘"),
        App(listOf("com.lietou.mishu"), "liepin", "猎聘"),
        App(listOf("com.zhaopin.social"), "zhaopin", "zhilian", "智联招聘"),
        App(listOf("com.job.android"), "51job", "qiancheng wuyou", "前程无忧"),
        App(listOf("com.linkedin.android"), "linkedin", "领英"),
        App(listOf("com.sankuai.meituan"), "meituan", "美团"),
        App(listOf("com.sankuai.meituan.takeoutnew"), "meituan waimai", "meituan delivery", "美团外卖"),
        App(listOf("me.ele"), "eleme", "ele.me", "饿了么"),
        App(listOf("com.dianping.v1"), "dianping", "dazhong dianping", "大众点评"),
        App(listOf("com.wudaokou.hippo"), "hema", "freshippo", "盒马"),
        App(listOf("com.lucky.luckyclient"), "luckin", "luckin coffee", "瑞幸", "瑞幸咖啡"),
        App(listOf("com.taobao.taobao"), "taobao", "淘宝"),
        App(listOf("com.tmall.wireless"), "tmall", "天猫"),
        App(listOf("com.jingdong.app.mall"), "jd", "jingdong", "京东"),
        App(listOf("com.xunmeng.pinduoduo"), "pinduoduo", "pdd", "拼多多"),
        App(listOf("com.taobao.idlefish"), "xianyu", "idle fish", "闲鱼"),
        App(listOf("ctrip.android.view"), "ctrip", "trip.com", "xiecheng", "携程", "携程旅行"),
        App(listOf("com.MobileTicket"), "12306", "china railway", "railway 12306", "铁路12306"),
        App(listOf("com.Qunar"), "qunar", "去哪儿", "去哪儿旅行"),
        App(listOf("com.taobao.trip"), "fliggy", "feizhu", "飞猪"),
        App(listOf("com.cainiao.wireless"), "cainiao", "菜鸟", "菜鸟裹裹"),
        App(listOf("com.sf.activity"), "sf express", "shunfeng", "顺丰", "顺丰速运"),
        App(listOf("com.xingin.xhs"), "xiaohongshu", "rednote", "red", "小红书"),
        App(listOf("com.ss.android.ugc.aweme"), "douyin", "抖音"),
        App(listOf("com.smile.gifmaker"), "kuaishou", "快手"),
        App(listOf("tv.danmaku.bili"), "bilibili", "b站", "哔哩哔哩"),
        App(listOf("com.sina.weibo"), "weibo", "微博"),
        App(listOf("com.zhihu.android"), "zhihu", "知乎"),
        App(listOf("com.baidu.searchbox"), "baidu", "百度"),
        App(listOf("com.alibaba.android.rimet"), "dingtalk", "dingding", "钉钉"),
        App(listOf("com.ss.android.lark"), "feishu", "lark", "飞书"),
    )

    /**
     * Candidate packages for what the user called the app, best first. Empty when
     * the name is not one this table knows; the caller then reports "no app
     * matches" exactly as before.
     */
    fun candidates(name: String): List<String> {
        val key = normalize(name)
        if (key.isEmpty()) return emptyList()
        return APPS.filter { key in it.keys }.flatMap { it.packages }
    }

    /** Lowercase; spaces, dots, dashes, middle dots and apostrophes removed. */
    internal fun normalize(s: String): String =
        s.lowercase().replace(Regex("[\\s.\\-·'’_]+"), "")
}
