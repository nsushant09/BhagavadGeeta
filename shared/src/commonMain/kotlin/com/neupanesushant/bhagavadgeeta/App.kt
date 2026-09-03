package com.neupanesushant.bhagavadgeeta

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bhagavadgeeta.shared.generated.resources.Res
import bhagavadgeeta.shared.generated.resources.hero_manuscript
import bhagavadgeeta.shared.generated.resources.noto_serif_devanagari
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Change these two tokens to update typography consistently throughout the app. */
@Composable
private fun scriptureFont() = FontFamily(Font(Res.font.noto_serif_devanagari))

private val AppTypography = Typography()

private val Ink = Color(0xFF1F1A17)
private val Saffron = Color(0xFFC85A17)
private val Gold = Color(0xFFD4AF37)
private val Parchment = Color(0xFFF9F4EC)
private val Paper = Color(0xFFF0E8DC)
private val Clay = Color(0xFF7A6A5D)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Saffron,
    background = Parchment,
    surface = Paper,
    surfaceVariant = Color(0xFFE5DDD0),
    onSurface = Ink,
    onSurfaceVariant = Clay,
    outline = Color(0xFFD1C5B8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5EBE1),
    onPrimary = Ink,
    secondary = Gold,
    background = Color(0xFF141110),
    surface = Color(0xFF1F1B18),
    surfaceVariant = Color(0xFF2B2521),
    onSurface = Color(0xFFF5EBE1),
    onSurfaceVariant = Color(0xFFBAAFA3),
    outline = Color(0xFF423A34)
)

private typealias Chapter = GitaChapterSummary

private val Chapter.sanskrit get() = name
private val Chapter.title get() = transliteratedName
private val Chapter.verses get() = verseCount

enum class ReadingMode { FULL, SHLOKA_ONLY, TRANSLATION_ONLY }
private enum class Destination { HOME, CHAPTERS, SAVED, MORE }
private data class ReadingProgress(
    val chapter: Int,
    val listIndex: Int,
    val scrollOffset: Int,
    val verse: Int
)

private data class ReaderRequest(
    val chapter: Chapter,
    val verse: Int? = null,
    val resume: Boolean = false
)

private fun dailySequence(date: String): Long {
    val parts = date.split('-').mapNotNull(String::toLongOrNull)
    return if (parts.size == 3) parts[0] * 372 + parts[1] * 31 + parts[2] else 0
}

private fun AppStorage.progressFor(chapter: Int, verses: Int): ReadingProgress {
    val index = read(StorageKeys.ReadingIndexPrefix + chapter)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val maxVerse = verses.coerceAtLeast(1)
    val verseNum = if (index <= 0) 1 else index.coerceIn(1, maxVerse)
    return ReadingProgress(
        chapter = chapter,
        listIndex = index,
        scrollOffset = read(StorageKeys.ReadingOffsetPrefix + chapter)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        verse = verseNum
    )
}

@Composable
@Preview
fun App(
    storage: AppStorage = MemoryAppStorage(),
    actions: PlatformActions = NoOpPlatformActions(),
    audioPlayer: VerseAudioPlayer = NoOpVerseAudioPlayer(),
    notificationScheduler: NotificationScheduler = NoOpNotificationScheduler(),
    repository: GitaRepository = BundledGitaRepository()
) {
    val chapters by produceState<List<Chapter>>(emptyList(), repository) {
        value = repository.catalog()
    }
    var localDate by remember { mutableStateOf(currentLocalDateKey()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val date = currentLocalDateKey()
            if (date != localDate) localDate = date
        }
    }
    val featuredVerse by produceState<GitaVerse?>(null, repository, localDate, chapters) {
        if (chapters.isEmpty()) return@produceState
        val total = chapters.sumOf { it.verseCount }
        var position = dailySequence(localDate) % total
        val dailyChapter = chapters.first { chapter ->
            if (position < chapter.verseCount) true else {
                position -= chapter.verseCount
                false
            }
        }
        value = repository.chapter(dailyChapter.number).verses.getOrNull(position.toInt())
    }
    var dark by remember { mutableStateOf(storage.read(StorageKeys.Theme) == "dark") }
    var fontScale by remember {
        mutableStateOf(storage.read(StorageKeys.FontScale)?.toFloatOrNull() ?: 1.0f)
    }
    var selectedLanguage by remember {
        mutableStateOf(storage.read("translation_language") ?: "all")
    }
    var notificationEnabled by remember {
        mutableStateOf(storage.read(StorageKeys.NotificationEnabled) == "true")
    }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var readerRequest by remember { mutableStateOf<ReaderRequest?>(null) }
    var progress by remember(chapters) {
        mutableStateOf(chapters.firstOrNull {
            it.number == storage.read(StorageKeys.LastChapter)?.toIntOrNull()
        }?.let { storage.progressFor(it.number, it.verseCount) })
    }
    var bookmarks by remember {
        mutableStateOf(
            storage.read(StorageKeys.Bookmarks)?.split(',')?.filter(String::isNotBlank)?.toSet()
                .orEmpty()
        )
    }
    var highlights by remember {
        mutableStateOf(
            storage.read(StorageKeys.Highlights)?.split(',')?.filter(String::isNotBlank)?.toSet()
                .orEmpty()
        )
    }

    fun updateFontScale(newScale: Float) {
        val clamped = newScale.coerceIn(0.85f, 1.60f)
        fontScale = clamped
        storage.write(StorageKeys.FontScale, clamped.toString())
    }

    fun updateLanguage(newLang: String) {
        selectedLanguage = newLang
        storage.write("translation_language", newLang)
    }

    fun updateNotificationEnabled(enabled: Boolean) {
        notificationEnabled = enabled
        storage.write(StorageKeys.NotificationEnabled, if (enabled) "true" else "false")
        val verseText = featuredVerse?.preferredTranslation(selectedLanguage)
            ?: "Start your day with today's verse contemplation from the Bhagavad Gita."
        val verseRef = featuredVerse?.reference?.let { "Verse $it" } ?: "Daily Wisdom"
        val title = "Morning Discipline · $verseRef"
        notificationScheduler.scheduleMorningNotification(enabled, title, verseText)
    }

    LaunchedEffect(notificationEnabled, featuredVerse, selectedLanguage) {
        if (notificationEnabled && featuredVerse != null) {
            val verseText = featuredVerse!!.preferredTranslation(selectedLanguage)
            val verseRef = "Verse ${featuredVerse!!.reference}"
            val title = "Morning Discipline · $verseRef"
            notificationScheduler.scheduleMorningNotification(true, title, verseText)
        }
    }

    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography) {
        PlatformSystemBars(dark)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = readerRequest,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220))) togetherWith
                                fadeOut(animationSpec = tween(150))
                    } else {
                        fadeIn(animationSpec = tween(180)) togetherWith
                                (slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)))
                    }
                },
                label = "ReaderTransition"
            ) { request ->
                if (request != null) {
                    ChapterReader(
                        request = request,
                        bookmarks = bookmarks,
                        highlights = highlights,
                        repository = repository,
                        audioPlayer = audioPlayer,
                        fontScale = fontScale,
                        selectedLanguage = selectedLanguage,
                        onFontScaleChange = ::updateFontScale,
                        onLanguageChange = ::updateLanguage,
                        toggle = { id ->
                            bookmarks = bookmarks.toggle(id)
                            storage.write(StorageKeys.Bookmarks, bookmarks.joinToString(","))
                        },
                        toggleHighlight = { id ->
                            highlights = highlights.toggle(id)
                            storage.write(StorageKeys.Highlights, highlights.joinToString(","))
                        },
                        storage = storage,
                        onProgress = { progress = it },
                        back = { readerRequest = null }
                    )
                } else {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { EditorialBottomBar(destination) { destination = it } }
                    ) { padding ->
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (fadeIn(animationSpec = tween(180)) + slideInHorizontally(initialOffsetX = { direction * (it / 16) }, animationSpec = tween(180))) togetherWith
                                        fadeOut(animationSpec = tween(120))
                            },
                            label = "TabTransition"
                        ) { dest ->
                            when (dest) {
                                Destination.HOME -> Home(
                                    Modifier.padding(padding),
                                    chapters,
                                    featuredVerse,
                                    progress,
                                    fontScale,
                                    selectedLanguage,
                                    { chapter, verse, resume ->
                                        readerRequest = ReaderRequest(chapter, verse, resume)
                                    },
                                    { destination = Destination.CHAPTERS }
                                )

                                Destination.CHAPTERS -> ChapterList(
                                    Modifier.padding(padding),
                                    chapters
                                ) { readerRequest = ReaderRequest(it) }

                                Destination.SAVED -> Saved(
                                    Modifier.padding(padding),
                                    bookmarks,
                                    highlights,
                                    chapters,
                                    repository,
                                    fontScale,
                                    selectedLanguage
                                ) { chapter, verse -> readerRequest = ReaderRequest(chapter, verse) }

                                Destination.MORE -> More(
                                    Modifier.padding(padding),
                                    dark = dark,
                                    fontScale = fontScale,
                                    selectedLanguage = selectedLanguage,
                                    notificationEnabled = notificationEnabled,
                                    actions = actions,
                                    changeTheme = {
                                        dark = !dark
                                        storage.write(StorageKeys.Theme, if (dark) "dark" else "light")
                                    },
                                    changeFontScale = ::updateFontScale,
                                    changeLanguage = ::updateLanguage,
                                    changeNotificationEnabled = ::updateNotificationEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialBottomBar(current: Destination, select: (Destination) -> Unit) = Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
    tonalElevation = 8.dp,
    shadowElevation = 12.dp
) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Destination.entries.forEach { item ->
            val selected = current == item
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(160),
                label = "bottomBarColor"
            )
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { select(item) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(icon(item), color = color, fontSize = 18.sp)
                Text(
                    label(item).uppercase(),
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun Home(
    modifier: Modifier,
    chapters: List<Chapter>,
    featuredVerse: GitaVerse?,
    progress: ReadingProgress?,
    fontScale: Float,
    selectedLanguage: String,
    open: (Chapter, Int?, Boolean) -> Unit,
    viewAll: () -> Unit
) = LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    item { Hero { chapters.getOrNull(0)?.let { open(it, null, false) } } }
    item {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Eyebrow("TODAY’S CONTEMPLATION")
            Text(
                featuredVerse?.let { "A teaching from ${chapters.firstOrNull { chapter -> chapter.number == it.chapter }?.title.orEmpty()}" }
                    ?: "A new teaching each day",
                fontFamily = FontFamily.Serif,
                fontSize = (29 * fontScale).sp,
                lineHeight = (34 * fontScale).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 7.dp)
            )
            Text(
                featuredVerse?.preferredTranslation(selectedLanguage).orEmpty().ifBlank { "Loading today’s verse…" },
                fontSize = (16 * fontScale).sp,
                lineHeight = (24 * fontScale).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            TextButton(
                {
                    featuredVerse?.let { verse ->
                        chapters.firstOrNull { it.number == verse.chapter }
                            ?.let { open(it, verse.number, false) }
                    }
                },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 3.dp)
            ) {
                Text(
                    "READ VERSE ${featuredVerse?.reference.orEmpty()}  →",
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
    progress?.let { reading ->
        chapters.firstOrNull { it.number == reading.chapter }?.let { chapter ->
            item {
                ContinueCard(chapter, reading) {
                    open(chapter, null, true)
                }
            }
        }
    }
    item {
        Column {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Eyebrow("EXPLORE")
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "The eighteen paths",
                        fontFamily = FontFamily.Serif,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "View all",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { viewAll() }
                            .padding(8.dp)
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 14.dp)
            ) { items(chapters.take(6)) { c -> ChapterTile(c) { open(c, null, false) } } }
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

@Composable
private fun Hero(openChapterOne: () -> Unit) = Surface(
    Modifier.fillMaxWidth().height(260.dp),
    color = Ink
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.hero_manuscript),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(.22f)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Ink.copy(alpha = .92f))
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(24.dp)
        ) {
            Text(
                "श्रीमद्भगवद्गीता",
                color = Gold,
                fontFamily = scriptureFont(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "The Song Celestial",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "701 verses across 18 chapters · Complete offline edition",
                color = Color.White.copy(alpha = .78f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Button(
                onClick = openChapterOne,
                colors = ButtonDefaults.buttonColors(containerColor = Saffron),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                Text("BEGIN READING  →", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ContinueCard(chapter: Chapter, progress: ReadingProgress, open: () -> Unit) = Surface(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp))
        .clickable { open() }, color = Ink
) {
    val displayVerse = progress.verse.coerceIn(1, chapter.verseCount.coerceAtLeast(1))
    val percent = (displayVerse * 100 / chapter.verseCount.coerceAtLeast(1)).coerceIn(1, 100)
    Row(
        Modifier.padding(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(Saffron),
            contentAlignment = Alignment.Center
        ) {
            Text(
                displayVerse.toString(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                "CONTINUE READING",
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                chapter.title,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Chapter ${chapter.number} · Verse $displayVerse · $percent% complete",
                color = Color.White.copy(alpha = .62f),
                fontSize = 13.sp
            )
        }
        Text("→", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
private fun ChapterTile(c: Chapter, open: () -> Unit) {
    val isEven = c.number % 2 == 0
    val cardBg = if (isEven) Ink else MaterialTheme.colorScheme.surfaceVariant
    val sanskirtColor = if (isEven) Gold else MaterialTheme.colorScheme.secondary
    val titleColor = if (isEven) Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isEven) Color.White.copy(alpha = .72f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        Modifier.width(168.dp).height(190.dp).clip(RoundedCornerShape(24.dp)).clickable { open() },
        color = cardBg
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Saffron),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    c.number.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                c.sanskrit,
                fontFamily = scriptureFont(),
                fontSize = 14.sp,
                color = sanskirtColor,
                maxLines = 1
            )
            Text(
                c.title,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                minLines = 2,
                maxLines = 2,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "${c.verses} verses",
                fontSize = 12.sp,
                color = subtitleColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.secondary,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.4.sp
)

@Composable
private fun QuoteCard(
    v: GitaVerse,
    fontScale: Float = 1.0f,
    selectedLanguage: String = "all",
    open: () -> Unit
) = Surface(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { open() },
    color = MaterialTheme.colorScheme.surface
) {
    Column(Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "VERSE ${v.reference}",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Text("READ  →", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Text(
            v.devanagari,
            fontFamily = scriptureFont(),
            fontSize = (17 * fontScale).sp,
            lineHeight = (28 * fontScale).sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            v.preferredTranslation(selectedLanguage),
            fontFamily = FontFamily.Serif,
            fontSize = (15 * fontScale).sp,
            lineHeight = (22 * fontScale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ChapterList(
    modifier: Modifier,
    chapters: List<Chapter>,
    open: (Chapter) -> Unit
) = LazyColumn(
    modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    item {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Eyebrow("CANONICAL TEXT")
            Text(
                "Eighteen Chapters",
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    items(chapters) { c ->
        Surface(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .clickable { open(c) },
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ink),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        c.number.toString(),
                        color = Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                        c.sanskrit,
                        fontFamily = scriptureFont(),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        c.title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${c.meaning}  ·  ${c.verses} verses",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text("→", color = MaterialTheme.colorScheme.secondary, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun ChapterReader(
    request: ReaderRequest,
    bookmarks: Set<String>,
    highlights: Set<String>,
    repository: GitaRepository,
    audioPlayer: VerseAudioPlayer,
    fontScale: Float,
    selectedLanguage: String,
    onFontScaleChange: (Float) -> Unit,
    onLanguageChange: (String) -> Unit,
    toggle: (String) -> Unit,
    toggleHighlight: (String) -> Unit,
    storage: AppStorage,
    onProgress: (ReadingProgress) -> Unit,
    back: () -> Unit
) {
    val chapter = request.chapter
    var mode by remember {
        mutableStateOf(runCatching {
            ReadingMode.valueOf(
                storage.read(StorageKeys.ReadingMode) ?: "FULL"
            )
        }.getOrDefault(ReadingMode.FULL))
    }
    val content by produceState<GitaChapter?>(null, chapter.number) {
        value = repository.chapter(chapter.number)
    }
    val verses = content?.verses.orEmpty()
    val listState = rememberLazyListState()
    var positionRestored by remember(chapter.number, request) { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(audioPlayer) { onDispose { audioPlayer.release() } }

    LaunchedEffect(chapter.number) {
        storage.write(StorageKeys.LastChapter, chapter.number.toString())
    }

    LaunchedEffect(content, request) {
        if (content == null || positionRestored) return@LaunchedEffect
        val stored = storage.progressFor(chapter.number, chapter.verseCount)
        val targetIndex = when {
            request.verse != null -> {
                val found = verses.indexOfFirst { it.number == request.verse }
                if (found >= 0) found + 1 else 1
            }
            request.resume -> stored.listIndex.coerceIn(0, maxOf(0, verses.size))
            else -> 0
        }
        val safeOffset = if (request.resume && request.verse == null) stored.scrollOffset else 0
        listState.scrollToItem(targetIndex, safeOffset)
        positionRestored = true
    }

    LaunchedEffect(positionRestored, verses) {
        if (!positionRestored || verses.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
            delay(250)
            val listIndex = index.coerceIn(0, verses.size)
            val verseNum = if (listIndex <= 0) 1 else verses[(listIndex - 1).coerceIn(0, maxOf(0, verses.lastIndex))].number
            val progress = ReadingProgress(chapter.number, listIndex, offset, verseNum)
            storage.write(StorageKeys.LastChapter, chapter.number.toString())
            storage.write(StorageKeys.ReadingIndexPrefix + chapter.number, listIndex.toString())
            storage.write(StorageKeys.ReadingOffsetPrefix + chapter.number, offset.toString())
            onProgress(progress)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = Ink) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(76.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(back) { Text("←", color = Color.White, fontSize = 24.sp) }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "CHAPTER ${chapter.number}",
                            color = Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            chapter.title,
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 18.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    Modifier.padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        chapter.sanskrit,
                        fontFamily = scriptureFont(),
                        fontSize = (22 * fontScale).sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        chapter.summary,
                        fontFamily = FontFamily.Serif,
                        fontSize = (18 * fontScale).sp,
                        lineHeight = (26 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    ReadingModeToggle(
                        mode,
                        Modifier.fillMaxWidth().padding(top = 18.dp)
                    ) { selected ->
                        mode = selected
                        storage.write(StorageKeys.ReadingMode, selected.name)
                    }

                    // Reader Controls: Language & Font Size
                    Surface(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            // Language selector row
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "LANGUAGE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "english" to "English",
                                        "hindi" to "Hindi",
                                        "all" to "Both"
                                    ).forEach { (langKey, label) ->
                                        val selected = selectedLanguage.equals(langKey, ignoreCase = true)
                                        Box(
                                            Modifier.clip(RoundedCornerShape(12.dp))
                                                .background(if (selected) Saffron else Color.Transparent)
                                                .clickable { onLanguageChange(langKey) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(
                                Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            // Font size control row
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "FONT SIZE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Saffron,
                                        modifier = Modifier.size(30.dp).clickable {
                                            onFontScaleChange((fontScale - 0.15f).coerceAtLeast(0.85f))
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("A-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        "${(fontScale * 100).roundToInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Saffron,
                                        modifier = Modifier.size(30.dp).clickable {
                                            onFontScaleChange((fontScale + 0.15f).coerceAtMost(1.60f))
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("A+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (content == null) item {
                Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Saffron) }
            }
            else items(verses, key = { it.id }) { v ->
                VerseCard(
                    v = v,
                    mode = mode,
                    saved = v.reference in bookmarks,
                    highlighted = v.reference in highlights,
                    playing = playingId == v.reference,
                    fontScale = fontScale,
                    selectedLanguage = selectedLanguage,
                    save = { toggle(v.reference) },
                    highlight = { toggleHighlight(v.reference) }
                ) {
                    if (playingId == v.reference) {
                        audioPlayer.stop()
                        playingId = null
                    } else scope.launch {
                        audioPlayer.stop()
                        playingId = v.reference
                        audioPlayer.play(v.reference, repository.audio(v)) { playingId = null }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun VerseCard(
    v: GitaVerse,
    mode: ReadingMode,
    saved: Boolean,
    highlighted: Boolean,
    playing: Boolean,
    fontScale: Float,
    selectedLanguage: String,
    save: () -> Unit,
    highlight: () -> Unit,
    audio: () -> Unit
) {
    var translationIndex by remember(v.id) { mutableIntStateOf(0) }
    val displayedTranslations = remember(v.translations, selectedLanguage) {
        if (selectedLanguage.equals("all", ignoreCase = true)) {
            v.translations
        } else {
            val filtered = v.translations.filter { it.language.equals(selectedLanguage, ignoreCase = true) }
            filtered.ifEmpty { v.translations }
        }
    }

    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(Ink)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "॥  ${v.reference}  ॥",
                    color = Gold,
                    fontFamily = scriptureFont(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (playing) "■  STOP" else "▶  LISTEN",
                    color = if (playing) Gold else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { audio() }
                        .padding(8.dp)
                )
                Text(
                    if (highlighted) "◆" else "◇",
                    color = if (highlighted) Gold else Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { highlight() }.padding(8.dp)
                )
                Text(
                    if (saved) "★" else "☆",
                    color = if (saved) Gold else Color.White,
                    fontSize = 21.sp,
                    modifier = Modifier.clickable { save() }.padding(8.dp)
                )
            }
            Column(Modifier.padding(22.dp)) {
                if (mode != ReadingMode.TRANSLATION_ONLY) {
                    Text(
                        v.devanagari,
                        fontFamily = scriptureFont(),
                        fontSize = (23 * fontScale).sp,
                        lineHeight = (38 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    )
                    Text(
                        v.transliteration,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = (16 * fontScale).sp,
                        lineHeight = (24 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (mode == ReadingMode.FULL) {
                    Label("WORD BY WORD")
                    Text(
                        v.wordMeanings,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (14 * fontScale).sp,
                        lineHeight = (22 * fontScale).sp
                    )
                }
                if (mode != ReadingMode.SHLOKA_ONLY) {
                    Label("TRANSLATIONS")
                    ContentPager(
                        items = displayedTranslations,
                        index = translationIndex,
                        select = { translationIndex = it },
                        highlighted = highlighted,
                        fontScale = fontScale
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentPager(
    items: List<GitaText>,
    index: Int,
    select: (Int) -> Unit,
    highlighted: Boolean,
    fontScale: Float = 1.0f
) {
    if (items.isEmpty()) {
        Text(
            "No text available for this section.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val safeIndex = index.coerceIn(items.indices)
    val item = items[safeIndex]
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) Gold.copy(alpha = .2f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = .45f
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.language.uppercase(),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${safeIndex + 1} / ${items.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Text(
                item.text,
                fontFamily = FontFamily.Serif,
                fontSize = (18 * fontScale).sp,
                lineHeight = (28 * fontScale).sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton({ select(if (safeIndex == 0) items.lastIndex else safeIndex - 1) }) {
                    Text("←")
                }
                TextButton({ select(if (safeIndex == items.lastIndex) 0 else safeIndex + 1) }) {
                    Text("NEXT  →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReadingModeToggle(
    current: ReadingMode,
    modifier: Modifier = Modifier,
    select: (ReadingMode) -> Unit
) = Surface(
    modifier,
    shape = RoundedCornerShape(20.dp),
    color = Ink
) {
    Row(Modifier.padding(5.dp)) {
        ReadingMode.entries.forEach { mode ->
            val selected = current == mode
            val text = when (mode) {
                ReadingMode.FULL -> "Full"
                ReadingMode.SHLOKA_ONLY -> "Shloka"
                ReadingMode.TRANSLATION_ONLY -> "Translation"
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Saffron else Color.Transparent)
                    .clickable { select(mode) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    color = if (selected) Color.White else Color.White.copy(alpha = .65f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Saved(
    modifier: Modifier,
    bookmarks: Set<String>,
    highlights: Set<String>,
    chapters: List<Chapter>,
    repository: GitaRepository,
    fontScale: Float,
    selectedLanguage: String,
    open: (Chapter, Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val ids = if (tab == 0) bookmarks else highlights
    val title = if (tab == 0) "bookmark" else "highlight"
    val markedVerses by produceState<List<GitaVerse>>(emptyList(), ids) {
        value = ids.mapNotNull { ref ->
            val parts = ref.split('.')
            val chapter = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val number = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            repository.chapter(chapter).verses.firstOrNull { it.number == number }
        }
    }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Eyebrow("PERSONAL COLLECTION")
        Text(
            "Your library",
            fontFamily = FontFamily.Serif,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp)
        )
        Surface(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            shape = RoundedCornerShape(18.dp),
            color = Ink
        ) {
            Row(Modifier.padding(5.dp)) {
                listOf(
                    "★  Bookmarks (${bookmarks.size})",
                    "◆  Highlights (${highlights.size})"
                ).forEachIndexed { i, text ->
                    val selected = tab == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Saffron else Color.Transparent)
                            .clickable { tab = i }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text,
                            color = if (selected) Color.White else Color.White.copy(alpha = .66f),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        if (ids.isEmpty()) Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Your ${title}s will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else LazyColumn(
            Modifier.padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(markedVerses, key = { it.id }) { verse ->
                QuoteCard(verse, fontScale, selectedLanguage) {
                    chapters.firstOrNull { it.number == verse.chapter }
                        ?.let { open(it, verse.number) }
                }
            }
        }
    }
}

@Composable
private fun More(
    modifier: Modifier,
    dark: Boolean,
    fontScale: Float,
    selectedLanguage: String,
    notificationEnabled: Boolean,
    actions: PlatformActions,
    changeTheme: () -> Unit,
    changeFontScale: (Float) -> Unit,
    changeLanguage: (String) -> Unit,
    changeNotificationEnabled: (Boolean) -> Unit
) = LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 30.dp),
    verticalArrangement = Arrangement.spacedBy(22.dp)
) {
    item {
        Box(Modifier.fillMaxWidth().background(Ink).statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 30.dp)) {
                Text(
                    "श्रीमद्भगवद्गीता",
                    color = Gold,
                    fontFamily = scriptureFont(),
                    fontSize = 16.sp
                )
                Text(
                    "The reader,\nmade complete.",
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Study, listen, reflect and return—entirely offline.",
                    color = Color.White.copy(alpha = .68f),
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
    item {
        MoreSection("APPEARANCE & PREFERENCES", "Customize your reading experience.") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Theme selector
                Surface(Modifier.fillMaxWidth(), color = Ink, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(5.dp)) {
                        listOf(
                            false to "☀  Parchment",
                            true to "◐  Midnight"
                        ).forEach { (isDark, label) ->
                            val selected = dark == isDark
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) Saffron else Color.Transparent)
                                    .clickable { if (!selected) changeTheme() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (selected) Color.White else Color.White.copy(alpha = .62f),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Language preference
                Surface(Modifier.fillMaxWidth(), color = Ink, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "TRANSLATION LANGUAGE",
                            color = Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            listOf(
                                "english" to "English",
                                "hindi" to "Hindi (हिंदी)",
                                "all" to "Both"
                            ).forEach { (langKey, label) ->
                                val selected = selectedLanguage.equals(langKey, ignoreCase = true)
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) Saffron else Color.Transparent)
                                        .clickable { changeLanguage(langKey) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) Color.White else Color.White.copy(alpha = .66f),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Font scale preference
                Surface(Modifier.fillMaxWidth(), color = Ink, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "FONT SIZE",
                                color = Gold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                "${(fontScale * 100).roundToInt()}%",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Saffron,
                                modifier = Modifier.size(36.dp).clickable {
                                    changeFontScale((fontScale - 0.15f).coerceAtLeast(0.85f))
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("A-", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Saffron,
                                modifier = Modifier.size(36.dp).clickable {
                                    changeFontScale((fontScale + 0.15f).coerceAtMost(1.60f))
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("A+", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Morning Discipline Notification Preference
                Surface(Modifier.fillMaxWidth(), color = Ink, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "MORNING DISCIPLINE",
                                color = Gold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                "5:00 AM Daily Notification",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                if (notificationEnabled) "Verse of the day at 5:00 AM" else "Disabled",
                                color = Color.White.copy(alpha = .62f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = notificationEnabled,
                            onCheckedChange = changeNotificationEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Saffron,
                                uncheckedThumbColor = Color.White.copy(alpha = .6f),
                                uncheckedTrackColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
    item {
        MoreSection("THE COMPLETE LIBRARY", "The full dialogue, available without a connection.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryStat("18", "chapters", Modifier.weight(1f))
                    LibraryStat("701", "verses", Modifier.weight(1f))
                    LibraryStat("701", "recitations", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryStat("4,907", "translations", Modifier.weight(1f))
                    LibraryStat("2", "languages", Modifier.weight(1f))
                    LibraryStat("100%", "offline", Modifier.weight(1f))
                }
            }
        }
    }
    item {
        MoreSection("ABOUT THE GĪTĀ", "A timeless dialogue on action, knowledge and devotion.") {
            EditorialInfoCard("THE SACRED DIALOGUE") {
                Text(
                    "The Bhagavad Gita forms part of the Mahabharata. Set on the field of Kurukshetra, it unfolds as a dialogue between Arjuna and Lord Krishna, his charioteer and guide.",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    lineHeight = 28.sp
                )
                HorizontalDivider(
                    Modifier.padding(vertical = 18.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
                )
                DetailLine("Traditional author", "Maharishi Ved Vyasa")
                DetailLine("Reading experience", "Sanskrit · transliteration · meanings")
                DetailLine("Languages", "English & Hindi translations")
            }
        }
    }
    item {
        MoreSection("SOURCE & ATTRIBUTION", "Open knowledge, respectfully presented.") {
            EditorialInfoCard("GITA / GITA") {
                Text(
                    "Scripture data and verse recitations are derived from the open-source gita/gita project, which describes its collection as ‘Bhagavad Gita in JSON’. The source repository is published under the Unlicense, a public-domain dedication.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 23.sp
                )
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    EditorialAction(
                        "Explore the source repository",
                        "github.com/gita/gita"
                    ) { actions.openUrl("https://github.com/gita/gita") }
                    EditorialAction(
                        "Read the Unlicense",
                        "Public-domain dedication and warranty terms"
                    ) { actions.openUrl("https://github.com/gita/gita/blob/main/LICENSE") }
                    EditorialAction(
                        "Report a content correction",
                        "Open an issue with the dataset maintainers"
                    ) { actions.openUrl("https://github.com/gita/gita/issues") }
                }
            }
        }
    }
    item {
        MoreSection("PRIVACY", "Your reading remains yours.") {
            EditorialInfoCard("LOCAL BY DESIGN") {
                Text(
                    "The scripture, audio, bookmarks, highlights, theme and reading position are stored on your device. Reading the Gita requires no account and no network connection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 23.sp
                )
            }
        }
    }
    item {
        MoreSection("SUPPORT THE READER", "Share the work and help it reach another reader.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorialAction(
                    "Share this app",
                    "Invite someone to read with you",
                    actions::shareApp
                )
                EditorialAction("Rate this app", "Leave a store review", actions::rateApp)
            }
        }
    }
    item {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "BHAGAVAD GEETA READER  ·  VERSION 1.0",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text(
                "Typeset with Noto Serif Devanagari",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun MoreSection(eyebrow: String, subtitle: String, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Eyebrow(eyebrow)
        Text(
            subtitle,
            fontFamily = FontFamily.Serif,
            fontSize = 23.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp, bottom = 13.dp)
        )
        content()
    }

@Composable
private fun LibraryStat(value: String, label: String, modifier: Modifier = Modifier) = Surface(
    modifier,
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp)
) {
    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            color = MaterialTheme.colorScheme.secondary,
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .7.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun EditorialInfoCard(label: String, content: @Composable ColumnScope.() -> Unit) = Surface(
    Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(26.dp)
) {
    Column(Modifier.padding(21.dp)) { Eyebrow(label); Spacer(Modifier.height(13.dp)); content() }
}

@Composable
private fun DetailLine(label: String, value: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.weight(.85f)
        )
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.15f)
        )
    }

@Composable
private fun EditorialAction(title: String, subtitle: String, action: () -> Unit) = Surface(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = action),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f),
    shape = RoundedCornerShape(18.dp)
) {
    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Ink),
            contentAlignment = Alignment.Center
        ) { Text("↗", color = Gold, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text("›", color = MaterialTheme.colorScheme.secondary, fontSize = 22.sp)
    }
}

@Composable
private fun SectionTitle(value: String) =
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun Label(value: String) = Text(
    value,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    color = MaterialTheme.colorScheme.primary
)

private fun <T> Set<T>.toggle(value: T) = if (value in this) this - value else this + value

private fun GitaVerse.preferredTranslation(language: String = "english"): String {
    if (language.equals("all", ignoreCase = true)) {
        return translations.firstOrNull { it.language.equals("english", true) }?.text
            ?: translations.firstOrNull()?.text.orEmpty()
    }
    val match = translations.firstOrNull { it.language.equals(language, ignoreCase = true) }?.text
    return match ?: translations.firstOrNull { it.language.equals("english", ignoreCase = true) }?.text
    ?: translations.firstOrNull()?.text.orEmpty()
}

private fun label(item: Destination) = when (item) {
    Destination.HOME -> "Home"
    Destination.CHAPTERS -> "Chapters"
    Destination.SAVED -> "Saved"
    Destination.MORE -> "More"
}

private fun icon(item: Destination) = when (item) {
    Destination.HOME -> "⌂"
    Destination.CHAPTERS -> "☷"
    Destination.SAVED -> "☆"
    Destination.MORE -> "•••"
}
