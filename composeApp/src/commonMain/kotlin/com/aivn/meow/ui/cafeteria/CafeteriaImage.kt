package com.aivn.meow.ui.cafeteria

import androidx.compose.ui.graphics.ImageBitmap

/** 메모리(LRU) → 디스크(`~/.cache/meow/kakao/`) → 네트워크 순으로 이미지를 읽는다. 실패 시 null. */
expect suspend fun loadCachedImage(url: String): ImageBitmap?

/** 메모리에 이미 디코드돼 있으면 바로 돌려준다(첫 프레임 깜빡임 방지). */
expect fun peekCachedImage(url: String): ImageBitmap?
