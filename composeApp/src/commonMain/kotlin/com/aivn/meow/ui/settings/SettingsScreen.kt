package com.aivn.meow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.config.AccountSettings
import com.aivn.meow.config.SupabaseConfig
import com.aivn.meow.github.GithubApiException
import com.aivn.meow.ms.MsAuthState
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.common.MeowType
import com.aivn.meow.ui.common.pageContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** GitHub 에서 classic 토큰을 만드는 페이지(필요 권한을 미리 골라 둔다). */
private const val NEW_CLASSIC_TOKEN_URL =
    "https://github.com/settings/tokens/new?scopes=repo,read:org&description=Meow%20iPhone"

/**
 * #165 아이폰 설정 화면: GitHub 토큰 · Microsoft 계정 · 실시간 알림(Supabase) 연결.
 * GitHub 연결 안내 카드와 각 화면 제목 줄의 ⚙︎ 에서 연다.
 *
 * GitHub 은 Personal Access Token 붙여 넣기만 지원한다.
 * TODO(#165): GitHub OAuth Device Flow — OAuth 앱 client id 가 생기면 "기기 로그인" 버튼을 추가한다
 *   (POST github.com/login/device/code → 사용자 코드 안내 → login/oauth/access_token 폴링).
 */
@Composable
fun SettingsScreen(
    settings: AccountSettings,
    githubLogin: String?,
    validateGithubToken: suspend (String) -> String,
    msAuth: MsAuthState,
    onMsConnect: () -> Unit,
    onMsSignOut: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(MeowColors.Background).verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.pageContent(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "설정",
                    modifier = Modifier.weight(1f),
                    color = MeowColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                PlainButton(text = "닫기", onClick = onClose)
            }
            GithubSection(settings, githubLogin, validateGithubToken, onOpenUrl)
            MicrosoftSection(msAuth, onMsConnect, onMsSignOut)
            SupabaseSection(settings)
            Text(
                text = "토큰 · 키는 이 아이폰의 키체인에만 저장돼요 (백업 · 다른 기기로 옮겨지지 않음).",
                style = MeowType.Meta,
                color = MeowColors.TextTertiary,
            )
        }
    }
}

// ---- GitHub ----

@Composable
private fun GithubSection(
    settings: AccountSettings,
    githubLogin: String?,
    validate: suspend (String) -> String,
    onOpenUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val connected = settings.hasGithubToken

    val submit = submit@{
        val value = token.trim()
        if (value.isEmpty() || checking) return@submit
        checking = true
        error = null
        scope.launch {
            try {
                validate(value) // viewer { login } 이 되는 토큰만 저장한다.
                settings.saveGithubToken(value) // 저장이 끝나면 앱이 새 토큰으로 다시 뜬다.
                token = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: GithubApiException.Unauthorized) {
                error = "토큰이 올바르지 않거나 권한이 부족해요 (HTTP ${e.status})"
            } catch (e: Exception) {
                error = "확인하지 못했어요: ${e.message ?: e.toString()}"
            } finally {
                checking = false
            }
        }
    }

    SettingsCard {
        SectionTitle(emoji = "🐙", title = "GitHub")
        StatusLine(
            ok = connected,
            text = when {
                connected && githubLogin != null -> "연결됨 · @$githubLogin"
                connected -> "토큰 저장됨"
                else -> "연결 안 됨"
            },
        )
        Text(
            text = "Personal Access Token 을 붙여 넣어 주세요. 저장 전에 토큰으로 내 GitHub 계정을 확인해요.",
            style = MeowType.Meta,
            color = MeowColors.TextSecondary,
        )
        Text(
            text = "필요 권한 — Classic: repo, read:org\n" +
                "Fine-grained: Team-AIVN 조직 · 모든 저장소, Pull requests · Contents 읽기/쓰기(머지), " +
                "Issues · Metadata 읽기, 조직 Members 읽기",
            style = MeowType.Meta,
            color = MeowColors.TextTertiary,
        )
        LinkText(text = "GitHub 에서 토큰 만들기 ↗", onClick = { onOpenUrl(NEW_CLASSIC_TOKEN_URL) })
        InputField(
            value = token,
            onValueChange = {
                token = it
                error = null
            },
            placeholder = if (connected) "새 토큰으로 바꾸기 (ghp_… / github_pat_…)" else "ghp_… 또는 github_pat_…",
            secret = true,
        )
        error?.let { Text(text = it, style = MeowType.Meta, color = MeowColors.Error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandButton(
                text = if (checking) "확인하는 중" else "확인 후 저장",
                onClick = submit,
                enabled = token.isNotBlank(),
                loading = checking,
            )
            if (connected) OutlineButton(text = "연결 해제", onClick = settings::clearGithubToken, enabled = !checking)
        }
    }
}

// ---- Microsoft ----

@Composable
private fun MicrosoftSection(auth: MsAuthState, onConnect: () -> Unit, onSignOut: () -> Unit) {
    SettingsCard {
        SectionTitle(emoji = "🔗", title = "Microsoft 계정")
        when (auth) {
            is MsAuthState.Connected -> StatusLine(ok = true, text = "연결됨 · ${auth.upn}")
            MsAuthState.Connecting -> StatusLine(ok = false, text = "연결하는 중이에요 · 열린 로그인 창에서 마쳐 주세요")
            is MsAuthState.Error -> StatusLine(ok = false, text = auth.message, error = true)
            MsAuthState.NotConnected -> StatusLine(ok = false, text = "연결 안 됨")
        }
        Text(
            text = "주간 보고(메일 · OneDrive 문서)와 출근(TeamAIVN 공용 캘린더)에 써요. 회사 계정으로 로그인해 주세요.",
            style = MeowType.Meta,
            color = MeowColors.TextSecondary,
        )
        if (auth is MsAuthState.Connected) {
            OutlineButton(text = "로그아웃", onClick = onSignOut)
        } else {
            BrandButton(
                text = if (auth is MsAuthState.Error) "다시 시도" else "Microsoft 계정 연결",
                onClick = onConnect,
                loading = auth is MsAuthState.Connecting,
            )
        }
    }
}

// ---- Supabase (실시간) ----

@Composable
private fun SupabaseSection(settings: AccountSettings) {
    val saved = settings.supabase
    var url by remember(saved) { mutableStateOf(saved?.url.orEmpty()) }
    var anonKey by remember(saved) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val save = save@{
        val u = url.trim().trimEnd('/')
        val k = anonKey.trim().ifEmpty { saved?.anonKey.orEmpty() }
        if (!u.startsWith("https://")) {
            error = "URL 은 https:// 로 시작해야 해요"
            return@save
        }
        if (k.isEmpty()) {
            error = "anon key 를 넣어 주세요"
            return@save
        }
        error = null
        runCatching { settings.saveSupabase(SupabaseConfig(url = u, anonKey = k)) }
            .onFailure { error = it.message ?: "저장하지 못했어요" }
    }

    SettingsCard {
        SectionTitle(emoji = "⚡️", title = "실시간 알림 (Supabase)")
        StatusLine(ok = saved != null, text = if (saved != null) "켜짐 · ${saved.url}" else "꺼짐 (설정 없음)")
        Text(
            text = "맥의 ~/.config/meow/supabase_url · supabase_anon 과 같은 값을 넣으면 리뷰 요청이 바로 반영돼요. " +
                "비워 두면 주기적인 새로고침만 해요.",
            style = MeowType.Meta,
            color = MeowColors.TextSecondary,
        )
        InputField(
            value = url,
            onValueChange = {
                url = it
                error = null
            },
            placeholder = "https://xxxx.supabase.co",
            secret = false,
            keyboardType = KeyboardType.Uri,
        )
        InputField(
            value = anonKey,
            onValueChange = {
                anonKey = it
                error = null
            },
            placeholder = if (saved != null) "anon key (저장됨 · 바꿀 때만 입력)" else "anon key",
            secret = true,
        )
        error?.let { Text(text = it, style = MeowType.Meta, color = MeowColors.Error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton(text = "저장", onClick = save, enabled = url.isNotBlank())
            if (saved != null) OutlineButton(text = "지우기", onClick = settings::clearSupabase)
        }
    }
}

// ---- 작은 구성 요소 ----

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glassSurface(corner = 20.dp).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun SectionTitle(emoji: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 18.sp)
        Text(text = title, style = MeowType.SectionHeading, color = MeowColors.TextPrimary)
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String, error: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) MeowColors.Success else if (error) MeowColors.Error else MeowColors.Grey),
        )
        Text(
            text = text,
            style = MeowType.Meta,
            color = if (error) MeowColors.Error else MeowColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean,
    keyboardType: KeyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MeowType.Body.copy(color = MeowColors.TextPrimary),
        cursorBrush = SolidColor(MeowColors.Brand),
        // 비밀 값은 가려 보여 주고, 자동 수정 · 대문자화를 끈다.
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
        ),
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeowColors.Background)
                    .border(1.dp, MeowColors.GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = MeowType.Body, color = MeowColors.TextTertiary)
                field()
            }
        },
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        style = MeowType.Meta,
        color = MeowColors.Brand,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PlainButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MeowType.Meta,
        color = MeowColors.Brand,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BrandButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    Row(
        modifier = Modifier
            .alpha(if (enabled || loading) 1f else 0.45f)
            .clip(RoundedCornerShape(12.dp))
            .background(MeowColors.Brand)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
        Text(text = text, style = MeowType.Meta, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(MeowColors.Surface)
            .border(1.dp, MeowColors.GlassBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        style = MeowType.Meta,
        color = MeowColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
    )
}
