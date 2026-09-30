package com.aivn.meow.ui

import androidx.compose.ui.graphics.ImageBitmap

/** URL 이미지를 받아 디코드. 실패 시 null (호출부에서 폴백 처리). */
expect suspend fun loadImageBitmap(url: String): ImageBitmap?
