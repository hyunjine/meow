package com.aivn.meow.config

/** `~/.config/meow/<name>` 의 내용을 앞뒤 공백을 잘라 돌려준다. 파일이 없거나 비어 있으면 null. */
expect fun readConfigFile(name: String): String?

/** `~/.config/meow/<name>` 에 [value] 를 쓴다(디렉터리가 없으면 만든다). 실패는 무시한다. */
expect fun writeConfigFile(name: String, value: String)

/** `~/.config/meow/<name>` 을 지운다. 없으면 아무 일도 하지 않는다. */
expect fun deleteConfigFile(name: String)
