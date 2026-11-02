@file:OptIn(ExperimentalForeignApi::class)

package com.aivn.meow.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * #165 iOS 키체인(generic password). 데스크톱의 macOS 키체인 항목과 같은 서비스 이름을 쓴다
 * ([SERVICE_GITHUB] · [SERVICE_MS_GRAPH] · [SERVICE_SUPABASE]).
 *
 * 항목은 이 기기에서만, 첫 잠금 해제 뒤부터 읽을 수 있다(`AfterFirstUnlockThisDeviceOnly` — 백업 · 다른 기기로 옮겨지지 않음).
 * 비밀 값은 로그에 남기지 않는다.
 */
internal object Keychain {
    const val SERVICE_GITHUB = "meow-github"
    const val SERVICE_MS_GRAPH = "meow-ms-graph"
    const val SERVICE_SUPABASE = "meow-supabase"

    data class Entry(val account: String, val secret: String)

    /** [service] (· [account]) 의 첫 항목. 없거나 읽지 못하면 null. */
    fun read(service: String, account: String? = null): Entry? = withQuery(service, account) { query ->
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@withQuery null
            val dict: CFDictionaryRef = result.value?.reinterpret() ?: return@withQuery null
            try {
                val acct = bridgedString(CFDictionaryGetValue(dict, kSecAttrAccount)).orEmpty()
                val data = bridgedData(CFDictionaryGetValue(dict, kSecValueData)) ?: return@withQuery null
                val secret = data.toByteArray().decodeToString()
                Entry(account = acct, secret = secret)
            } finally {
                CFRelease(dict)
            }
        }
    }

    /** [service] · [account] 항목을 [secret] 으로 저장한다(있으면 교체). 실패하면 예외. */
    fun write(service: String, account: String, secret: String) {
        delete(service, account)
        val status = withQuery(service, account) { query ->
            val data = NSString.create(string = secret).dataUsingEncoding(NSUTF8StringEncoding)
                ?: error("키체인에 저장할 값을 만들지 못했어요")
            val dataRef = CFBridgingRetain(data)
            try {
                CFDictionaryAddValue(query, kSecValueData, dataRef)
                CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                SecItemAdd(query, null)
            } finally {
                dataRef?.let { CFRelease(it) }
            }
        }
        check(status == errSecSuccess) { "키체인에 저장하지 못했어요 (OSStatus $status)" }
    }

    /** [service] (· [account]) 의 항목을 모두 지운다. */
    fun delete(service: String, account: String? = null) {
        // SecItemDelete 는 generic password 의 일치 항목을 모두 지운다. 혹시 남으면 몇 번 더 지운다.
        repeat(5) {
            val status = withQuery(service, account) { SecItemDelete(it) }
            if (status != errSecSuccess) return // errSecItemNotFound 등: 더 지울 게 없다
        }
    }

    private inline fun <T> withQuery(service: String, account: String?, block: (CFMutableDictionaryRef) -> T): T {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            ?: error("키체인 질의를 만들지 못했어요")
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val accountRef = account?.let { CFBridgingRetain(NSString.create(string = it)) }
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, serviceRef)
            accountRef?.let { CFDictionaryAddValue(query, kSecAttrAccount, it) }
            return block(query)
        } finally {
            serviceRef?.let { CFRelease(it) }
            accountRef?.let { CFRelease(it) }
            CFRelease(query)
        }
    }

    /** CFDictionaryGetValue 결과(소유하지 않음)를 retain 한 뒤 Kotlin(ARC) 쪽으로 넘겨 문자열로 복사한다. */
    private fun bridgedString(ref: kotlinx.cinterop.COpaquePointer?): String? {
        ref ?: return null
        val retained: CFTypeRef = platform.CoreFoundation.CFRetain(ref) ?: return null
        return CFBridgingRelease(retained) as? String
    }

    private fun bridgedData(ref: kotlinx.cinterop.COpaquePointer?): NSData? {
        ref ?: return null
        val retained: CFTypeRef = platform.CoreFoundation.CFRetain(ref) ?: return null
        return CFBridgingRelease(retained) as? NSData
    }
}
