package com.aivn.meow.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** #165 링크를 Safari(또는 그 주소를 맡은 앱)로 연다. */
internal fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

/** [url] 을 열고, 연 앱이 있었는지 돌려준다 (`ms-outlook://` 처럼 앱이 없으면 false). */
internal fun openUrl(url: String, onResult: (Boolean) -> Unit) {
    val nsUrl = NSURL.URLWithString(url) ?: return onResult(false)
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = { ok -> onResult(ok) })
}
