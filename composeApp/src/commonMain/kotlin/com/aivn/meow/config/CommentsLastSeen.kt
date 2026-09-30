package com.aivn.meow.config

/** #29 '내 이슈 새 댓글' 섹션의 마지막 확인 시각(ISO-8601). 저장된 값이 없으면 null. */
expect fun loadCommentsLastSeen(): String?

expect fun saveCommentsLastSeen(iso: String)
