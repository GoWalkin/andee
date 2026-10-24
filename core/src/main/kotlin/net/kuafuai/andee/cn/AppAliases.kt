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

    private class App(val packages: List<String>, val display: String, vararg names: String) {
        val keys: Set<String> = (names.toList() + display).map(::normalize).toSet()
    }

    private val APPS = listOf(
        App(listOf("com.tencent.mm"), "WeChat", "wechat", "weixin", "微信"),
        App(listOf("com.tencent.wework"), "WeCom", "wecom", "wechat work", "qiye weixin", "企业微信"),
        App(listOf("com.tencent.mobileqq"), "QQ", "qq"),
        App(listOf("com.eg.android.AlipayGphone"), "Alipay", "alipay", "zhifubao", "支付宝"),
        App(listOf("com.unionpay"), "UnionPay", "unionpay", "yunshanfu", "cloud quickpass", "云闪付"),
        App(listOf("com.sdu.didi.psnger"), "DiDi", "didi", "didi chuxing", "didi rider", "滴滴", "滴滴出行"),
        App(listOf("com.huaxiaozhu.rider"), "Huaxiaozhu", "huaxiaozhu", "花小猪", "花小猪打车"),
        App(listOf("com.caocaokeji.user"), "Caocao", "caocao", "曹操出行"),
        App(listOf("com.jingyao.easybike"), "Hello", "hello", "hellobike", "haluo", "哈啰", "哈啰出行"),
        App(listOf("com.autonavi.minimap"), "Amap", "amap", "gaode", "gaode map", "高德", "高德地图"),
        App(listOf("com.baidu.BaiduMap"), "Baidu Maps", "baidu map", "baidu maps", "百度地图"),
        App(listOf("com.tencent.map"), "Tencent Maps", "tencent map", "tencent maps", "腾讯地图"),
        App(listOf("com.hpbr.bosszhipin"), "BOSS Zhipin", "boss", "boss zhipin", "bosszhipin", "boss直聘", "直聘"),
        App(listOf("com.lietou.mishu"), "Liepin", "liepin", "猎聘"),
        App(listOf("com.zhaopin.social"), "Zhaopin", "zhaopin", "zhilian", "智联招聘"),
        App(listOf("com.job.android"), "51job", "51job", "qiancheng wuyou", "前程无忧"),
        App(listOf("com.linkedin.android"), "LinkedIn", "linkedin", "领英"),
        App(listOf("com.sankuai.meituan"), "Meituan", "meituan", "美团"),
        App(listOf("com.sankuai.meituan.takeoutnew"), "Meituan Waimai", "meituan waimai", "meituan delivery", "美团外卖"),
        App(listOf("me.ele"), "Ele.me", "eleme", "ele.me", "饿了么"),
        App(listOf("com.dianping.v1"), "Dianping", "dianping", "dazhong dianping", "大众点评"),
        App(listOf("com.wudaokou.hippo"), "Freshippo", "hema", "freshippo", "盒马"),
        App(listOf("com.lucky.luckyclient"), "Luckin Coffee", "luckin", "luckin coffee", "瑞幸", "瑞幸咖啡"),
        App(listOf("com.taobao.taobao"), "Taobao", "taobao", "淘宝"),
        App(listOf("com.tmall.wireless"), "Tmall", "tmall", "天猫"),
        App(listOf("com.jingdong.app.mall"), "JD", "jd", "jingdong", "京东"),
        App(listOf("com.xunmeng.pinduoduo"), "Pinduoduo", "pinduoduo", "pdd", "拼多多"),
        App(listOf("com.taobao.idlefish"), "Xianyu", "xianyu", "idle fish", "闲鱼"),
        App(listOf("ctrip.android.view"), "Trip.com", "ctrip", "trip.com", "xiecheng", "携程", "携程旅行"),
        App(listOf("com.MobileTicket"), "12306", "12306", "china railway", "railway 12306", "铁路12306"),
        App(listOf("com.Qunar"), "Qunar", "qunar", "去哪儿", "去哪儿旅行"),
        App(listOf("com.taobao.trip"), "Fliggy", "fliggy", "feizhu", "飞猪"),
        App(listOf("com.cainiao.wireless"), "Cainiao", "cainiao", "菜鸟", "菜鸟裹裹"),
        App(listOf("com.sf.activity"), "SF Express", "sf express", "shunfeng", "顺丰", "顺丰速运"),
        App(listOf("com.xingin.xhs"), "RedNote", "xiaohongshu", "rednote", "red", "小红书"),
        App(listOf("com.ss.android.ugc.aweme"), "Douyin", "douyin", "抖音"),
        App(listOf("com.smile.gifmaker"), "Kuaishou", "kuaishou", "快手"),
        App(listOf("tv.danmaku.bili"), "Bilibili", "bilibili", "b站", "哔哩哔哩"),
        App(listOf("com.sina.weibo"), "Weibo", "weibo", "微博"),
        App(listOf("com.zhihu.android"), "Zhihu", "zhihu", "知乎"),
        App(listOf("com.baidu.searchbox"), "Baidu", "baidu", "百度"),
        App(listOf("com.alibaba.android.rimet"), "DingTalk", "dingtalk", "dingding", "钉钉"),
        App(listOf("com.ss.android.lark"), "Feishu", "feishu", "lark", "飞书"),
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

    /**
     * The app's English name, for text an English-speaking user reads (a
     * notification row says "DiDi", not 滴滴出行 or `psnger`). Null for a package
     * this table does not know.
     */
    fun displayName(pkg: String): String? = APPS.firstOrNull { pkg in it.packages }?.display

    /** Lowercase; spaces, dots, dashes, middle dots and apostrophes removed. */
    internal fun normalize(s: String): String =
        s.lowercase().replace(Regex("[\\s.\\-·'’_]+"), "")
}
