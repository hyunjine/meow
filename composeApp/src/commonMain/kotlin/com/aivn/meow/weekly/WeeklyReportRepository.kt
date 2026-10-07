package com.aivn.meow.weekly

import com.aivn.meow.config.deleteConfigFile
import com.aivn.meow.config.readConfigFile
import com.aivn.meow.config.writeConfigFile
import com.aivn.meow.ms.DocumentChangedException
import com.aivn.meow.ms.GraphApiException
import com.aivn.meow.ms.GraphClient
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 김다혜 님 OneDrive 의 '2026 주간회의 자료' 공유 폴더에서 주차 문서를 찾고, Word 표의 내 행을 읽고 쓴다.
 * 모든 호출은 [graph] 로 하며 Microsoft 계정이 연결돼 있어야 한다.
 */
class WeeklyReportRepository(
    private val graph: GraphClient,
    private val timeZone: TimeZone = TimeZone.of("Asia/Seoul"),
    private val clock: Clock = Clock.System,
) {
    /**
     * 주간회의 자료 폴더. 캐시(`~/.config/meow/weekly_folder`) → 폴더 공유 메일의 `/:f:/` 링크 →
     * `sharedWithMe` 에서 이름에 '주간회의 자료' 가 든 폴더 순으로 찾고, 찾으면 캐시에 저장한다. 못 찾으면 null.
     */
    suspend fun findFolder(forceRefresh: Boolean = false): WeeklyFolder? {
        if (!forceRefresh) loadCachedFolder()?.let { return it }
        val found = findFolderFromMail() ?: findFolderFromSharedWithMe() ?: return null
        writeConfigFile(FOLDER_CACHE, "${found.driveId}\n${found.itemId}\n")
        return found
    }

    /** 폴더의 `Week N-M` 문서 목록(주차 내림차순). 폴더를 못 찾으면 빈 목록. */
    suspend fun listWeekDocs(folder: WeeklyFolder? = null): List<WeekDoc> {
        val target = folder ?: findFolder() ?: return emptyList()
        val children = try {
            listChildren(target)
        } catch (e: GraphApiException) {
            // 캐시된 폴더가 옮겨졌거나 권한이 바뀐 경우 한 번 다시 찾는다.
            if (folder != null || (e.status != 404 && e.status != 403)) throw e
            deleteConfigFile(FOLDER_CACHE)
            listChildren(findFolder(forceRefresh = true) ?: return emptyList())
        }
        return children.mapNotNull { it.toWeekDoc() }
            .sortedWith(compareByDescending<WeekDoc> { it.week }.thenByDescending { it.modifiedAt })
    }

    /**
     * [today](Asia/Seoul) 의 ISO 주차가 실적 주차인 문서. 폴더에 없으면 최근 7일 '주간회의' 메일의 Word 링크를 해석한다.
     * 둘 다 없으면 null(아직 메일 · 문서가 오지 않음).
     */
    suspend fun findThisWeekDoc(today: LocalDate = clock.todayIn(timeZone)): ThisWeekDoc? {
        val week = today.isoWeekNumber()
        val fromFolder = runCatching { listWeekDocs() }.getOrDefault(emptyList()).firstOrNull { it.week == week }
        if (fromFolder != null) return ThisWeekDoc(fromFolder, WeekDocSource.Folder)
        return findWeekDocFromMail(week, today)
    }

    /** 문서를 받아 첫 표의 헤더와 [myName] 행의 실적 · 계획 셀을 읽는다. */
    suspend fun readMyRow(doc: WeekDoc, myName: String): MyWeeklyRow {
        val file = graph.downloadItem(doc.driveId, doc.itemId)
        val table = readDocxFirstTable(file.bytes)
        val layout = parseWeeklyLayout(table, doc.name, fallbackYear = clock.todayIn(timeZone).year)
        val rowIndex = findRowIndex(table, layout.nameCol, myName)
        val cells = table.rows.getOrNull(rowIndex)
        return MyWeeklyRow(
            doc = doc,
            header = layout.header,
            myName = myName,
            found = cells != null,
            results = cells?.getOrNull(layout.resultCol)?.let(::cleanLines).orEmpty(),
            plans = cells?.getOrNull(layout.planCol)?.let(::cleanLines).orEmpty(),
            eTag = file.eTag,
        )
    }

    /**
     * 내 행의 실적 · 계획 셀만 바꿔 올린다. [expectedETag](읽을 때의 eTag) 를 주면 그 사이 문서가 바뀐 경우
     * 받지 않고 [DocumentChangedException] 을 던진다. 업로드도 `If-Match` 조건부라 동시 저장을 막는다.
     * @return 저장 후 새 eTag.
     */
    suspend fun writeMyRow(
        doc: WeekDoc,
        myName: String,
        results: List<String>,
        plans: List<String>,
        expectedETag: String? = null,
    ): String {
        val file = graph.downloadItem(doc.driveId, doc.itemId)
        if (expectedETag != null && file.eTag != expectedETag) throw DocumentChangedException()
        val updated = buildUpdatedDocx(file.bytes, doc.name, myName, results, plans)
        val saved = graph.uploadIfMatch(doc.driveId, doc.itemId, updated, file.eTag)
        return saved.eTag.orEmpty()
    }

    /** 업로드 없이 바뀐 docx 바이트만 만든다([writeMyRow] 내부 · 로컬 검증용). */
    fun buildUpdatedDocx(
        docx: ByteArray,
        docName: String,
        myName: String,
        results: List<String>,
        plans: List<String>,
    ): ByteArray {
        val table = readDocxFirstTable(docx)
        val layout = parseWeeklyLayout(table, docName, fallbackYear = clock.todayIn(timeZone).year)
        val rowIndex = findRowIndex(table, layout.nameCol, myName)
        if (rowIndex < 0) throw WeeklyDocFormatException("표에서 '$myName' 행을 찾지 못했어요")
        return replaceDocxTableCells(docx, rowIndex, mapOf(layout.resultCol to results, layout.planCol to plans))
    }

    // ---- 폴더 찾기 ----

    private fun loadCachedFolder(): WeeklyFolder? {
        val lines = readConfigFile(FOLDER_CACHE)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        if (lines.size < 2) return null
        return WeeklyFolder(driveId = lines[0], itemId = lines[1])
    }

    private suspend fun findFolderFromMail(): WeeklyFolder? {
        val messages = searchMessages(FOLDER_MAIL_SEARCH)
        for (message in messages.sortedByDescending { it.receivedDateTime }) {
            for (link in extractSharepointLinks(message.body?.content.orEmpty(), kind = "f")) {
                val item = resolveShare(link) ?: continue
                if (item.folder != null && item.name.orEmpty().contains(FOLDER_KEYWORD)) {
                    val driveId = item.parentReference?.driveId ?: continue
                    return WeeklyFolder(driveId, item.id, item.name)
                }
            }
        }
        return null
    }

    private suspend fun findFolderFromSharedWithMe(): WeeklyFolder? {
        val items = graph.getAll("/me/drive/sharedWithMe?\$top=200", DriveItemDto.serializer(), maxPages = 5)
        val candidates = items.filter { item ->
            val isFolder = item.folder != null || item.remoteItem?.folder != null
            isFolder && (item.remoteItem?.name ?: item.name).orEmpty().contains(FOLDER_KEYWORD)
        }
        // '2026 주간회의 자료' 처럼 올해 연도가 든 폴더를 우선한다.
        val year = clock.todayIn(timeZone).year.toString()
        val best = candidates.sortedByDescending { (it.remoteItem?.name ?: it.name).orEmpty().contains(year) }.firstOrNull()
        if (best != null) {
            val remote = best.remoteItem
            val driveId = remote?.parentReference?.driveId ?: best.parentReference?.driveId
            if (driveId != null) return WeeklyFolder(driveId, remote?.id ?: best.id, remote?.name ?: best.name)
        }
        // 폴더 대신 주차 문서만 공유된 경우: 그 문서의 상위 폴더.
        val weekFile = items.firstOrNull { WEEK_RANGE_REGEX.containsMatchIn((it.remoteItem?.name ?: it.name).orEmpty()) }
            ?: return null
        val parent = weekFile.remoteItem?.parentReference ?: weekFile.parentReference ?: return null
        return WeeklyFolder(parent.driveId ?: return null, parent.id ?: return null)
    }

    // ---- 주차 문서 ----

    private suspend fun listChildren(folder: WeeklyFolder): List<DriveItemDto> = graph.getAll(
        "/drives/${folder.driveId}/items/${folder.itemId}/children" +
            "?\$select=id,name,createdDateTime,lastModifiedDateTime,webUrl,file,folder,parentReference&\$top=200",
        DriveItemDto.serializer(),
    )

    private fun DriveItemDto.toWeekDoc(): WeekDoc? {
        if (file == null) return null
        val itemName = name ?: return null
        if (!itemName.endsWith(".docx", ignoreCase = true)) return null
        val match = WEEK_RANGE_REGEX.find(itemName) ?: return null
        return WeekDoc(
            week = match.groupValues[1].toInt(),
            nextWeek = match.groupValues[2].toInt(),
            driveId = parentReference?.driveId ?: return null,
            itemId = id,
            name = itemName,
            createdAt = createdDateTime?.let(::parseInstantOrNull),
            modifiedAt = lastModifiedDateTime?.let(::parseInstantOrNull),
            webUrl = webUrl,
        )
    }

    private suspend fun findWeekDocFromMail(week: Int, today: LocalDate): ThisWeekDoc? {
        // 최근 7일: today 포함 7일 전 0시(Asia/Seoul)부터.
        val since = today.minus(DatePeriod(days = 7)).atStartOfDayIn(timeZone)
        val messages = searchMessages(WEEK_MAIL_SEARCH)
            .filter { it.subject.orEmpty().contains(WEEK_MAIL_KEYWORD) }
            .filter { m -> m.receivedDateTime?.let(::parseInstantOrNull)?.let { it >= since } == true }
            .sortedByDescending { it.receivedDateTime }
        for (message in messages) {
            for (link in extractSharepointLinks(message.body?.content.orEmpty(), kind = "w")) {
                val doc = resolveShare(link)?.toWeekDoc() ?: continue
                // 지난주 문서 링크(다시 보내기 등)는 이번 주 문서가 아니므로 주차가 맞는 것만 받는다.
                if (doc.week == week) {
                    return ThisWeekDoc(doc, WeekDocSource.Mail, message.receivedDateTime?.let(::parseInstantOrNull))
                }
            }
        }
        return null
    }

    // ---- Graph 헬퍼 ----

    private suspend fun searchMessages(search: String): List<MessageDto> {
        val query = "\"$search\"".encodeURLParameter()
        return graph.get(
            "/me/messages?\$search=$query&\$select=subject,receivedDateTime,body&\$top=25",
            MessageList.serializer(),
        ).value
    }

    /** 공유 링크 → driveItem. 접근 불가 · 만료 링크면 null. */
    private suspend fun resolveShare(url: String): DriveItemDto? = try {
        graph.get(
            "/shares/${GraphClient.shareIdFor(url)}/driveItem" +
                "?\$select=id,name,createdDateTime,lastModifiedDateTime,webUrl,file,folder,parentReference",
            DriveItemDto.serializer(),
        )
    } catch (e: GraphApiException) {
        null
    }

    companion object {
        private const val FOLDER_CACHE = "weekly_folder"
        private const val REPORT_NAME = "report_name"
        private const val FOLDER_KEYWORD = "주간회의 자료"
        private const val FOLDER_MAIL_SEARCH = "폴더를 사용자와 공유했습니다 주간회의"
        private const val WEEK_MAIL_SEARCH = "주간회의"
        private const val WEEK_MAIL_KEYWORD = "주간회의"

        /** 표에서 찾을 내 이름(`~/.config/meow/report_name`). 없으면 null → UI 에서 입력받는다. */
        fun loadReportName(): String? = readConfigFile(REPORT_NAME)

        fun saveReportName(name: String) = writeConfigFile(REPORT_NAME, name.trim())
    }
}

/** 셀 문단 → 줄 목록. 끝 공백을 자르고, '•' · '-' 만 있는 빈 틀은 빈 셀로 본다. */
internal fun cleanLines(paragraphs: List<String>): List<String> {
    val lines = paragraphs.map { it.trimEnd() }
    val meaningful = lines.any { it.isNotBlank() && it.trim() !in PLACEHOLDERS }
    return if (meaningful) lines.dropLastWhile { it.isBlank() } else emptyList()
}

private val PLACEHOLDERS = setOf("•", "-", "·", "◦", "▪")

private val HREF_REGEX = Regex("""https://[A-Za-z0-9.-]+\.sharepoint\.com/:([a-z]):/[^\s"'<>]+""")
private val SAFELINK_REGEX = Regex("""https://[A-Za-z0-9.-]*safelinks\.protection\.outlook\.com/\?[^\s"'<>]*""")

/** 메일 HTML 본문에서 `/:{kind}:/` 형식 SharePoint 공유 링크를 뽑는다(Safe Links 로 감싼 링크도 푼다). */
internal fun extractSharepointLinks(html: String, kind: String): List<String> {
    val unescaped = html.replace("&amp;", "&")
    val unwrapped = SAFELINK_REGEX.findAll(unescaped).mapNotNull { m ->
        m.value.substringAfter("url=", "").substringBefore('&').takeIf { it.isNotEmpty() }?.let(::percentDecode)
    }
    return (HREF_REGEX.findAll(unescaped).map { it.value } + unwrapped)
        .filter { HREF_REGEX.matchEntire(it)?.groupValues?.get(1) == kind }
        .distinct()
        .toList()
}

private fun percentDecode(s: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        val hex = if (c == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes += hex.toByte()
            i += 3
            continue
        }
        c.toString().encodeToByteArray().forEach { bytes += it }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

private fun parseInstantOrNull(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()

@Serializable
internal data class ItemReference(val driveId: String? = null, val id: String? = null)

@Serializable
internal data class FileFacet(val mimeType: String? = null)

@Serializable
internal data class FolderFacet(val childCount: Int? = null)

@Serializable
internal data class RemoteItemDto(
    val id: String,
    val name: String? = null,
    val folder: FolderFacet? = null,
    val file: FileFacet? = null,
    val parentReference: ItemReference? = null,
)

@Serializable
internal data class DriveItemDto(
    val id: String,
    val name: String? = null,
    val createdDateTime: String? = null,
    val lastModifiedDateTime: String? = null,
    val webUrl: String? = null,
    val file: FileFacet? = null,
    val folder: FolderFacet? = null,
    val parentReference: ItemReference? = null,
    val remoteItem: RemoteItemDto? = null,
)

@Serializable
internal data class MessageList(val value: List<MessageDto> = emptyList())

@Serializable
internal data class MessageDto(
    val subject: String? = null,
    val receivedDateTime: String? = null,
    val body: MessageBody? = null,
)

@Serializable
internal data class MessageBody(
    @SerialName("contentType") val contentType: String? = null,
    val content: String? = null,
)
