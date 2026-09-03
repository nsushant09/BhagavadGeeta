package com.neupanesushant.bhagavadgeeta

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Change these two tokens to update typography consistently throughout the app. */
@Composable
private fun scriptureFont() = FontFamily(Font(Res.font.noto_serif_devanagari))
private val AppTypography = Typography()

private val Ink = Color(0xFF132A35)
private val Saffron = Color(0xFFD97732)
private val Gold = Color(0xFFF0B85A)
private val Parchment = Color(0xFFF6EEDC)
private val Paper = Color(0xFFFFFAEF)
private val Clay = Color(0xFF9E4935)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Saffron,
    tertiary = Clay,
    background = Parchment,
    surface = Paper,
    surfaceVariant = Color(0xFFE9DCC3),
    onSurfaceVariant = Color(0xFF665A4D),
    outline = Color(0xFFC7B99F)
)
private val DarkColors = darkColorScheme(
    primary = Gold,
    secondary = Saffron,
    background = Color(0xFF0D2029),
    surface = Color(0xFF162E38),
    surfaceVariant = Color(0xFF28434A),
    onSurfaceVariant = Color(0xFFD7CDBB),
    outline = Color(0xFF52666A)
)

private typealias Chapter = GitaChapterSummary

private val Chapter.sanskrit get() = name
private val Chapter.title get() = meaning
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
    val index = read(StorageKeys.ReadingIndexPrefix + chapter)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    return ReadingProgress(
        chapter,
        index,
        read(StorageKeys.ReadingOffsetPrefix + chapter)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        index.coerceIn(1, verses.coerceAtLeast(1))
    )
}

@Composable
@Preview
fun App(
    storage: AppStorage = MemoryAppStorage(),
    actions: PlatformActions = NoOpPlatformActions(),
    audioPlayer: VerseAudioPlayer = NoOpVerseAudioPlayer(),
    repository: GitaRepository = BundledGitaRepository()
) {
    val chapters by produceState<List<Chapter>>(emptyList(), repository) {
        value = repository.catalog()
    }
    var localDate by remember { mutableStateOf(currentLocalDateKey()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000);
            val date = currentLocalDateKey(); if (date != localDate) localDate = date
        }
    }
    val featuredVerse by produceState<GitaVerse?>(null, repository, localDate, chapters) {
        if (chapters.isEmpty()) return@produceState
        val total = chapters.sumOf { it.verseCount }
        // Adjacent local dates always advance the selection, including month/year boundaries.
        var position = dailySequence(localDate) % total
        val dailyChapter = chapters.first { chapter ->
            if (position < chapter.verseCount) true else {
                position -= chapter.verseCount; false
            }
        }
        value = repository.chapter(dailyChapter.number).verses.getOrNull(position.toInt())
    }
    var dark by remember { mutableStateOf(storage.read(StorageKeys.Theme) == "dark") }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var readerRequest by remember { mutableStateOf<ReaderRequest?>(null) }
    var progress by remember(chapters) {
        mutableStateOf(chapters.firstOrNull {
            it.number == storage.read(
                StorageKeys.LastChapter
            )?.toIntOrNull()
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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography) {
        PlatformSystemBars(dark)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (readerRequest != null) ChapterReader(
                readerRequest!!, bookmarks, highlights, repository, audioPlayer,
                { id ->
                    bookmarks = bookmarks.toggle(id); storage.write(
                    StorageKeys.Bookmarks,
                    bookmarks.joinToString(",")
                )
                },
                { id ->
                    highlights = highlights.toggle(id); storage.write(
                    StorageKeys.Highlights,
                    highlights.joinToString(",")
                )
                }, storage,
                { progress = it }) { readerRequest = null }
            else Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = { EditorialBottomBar(destination) { destination = it } }) { padding ->
                when (destination) {
                    Destination.HOME -> Home(
                        Modifier.padding(padding), chapters, featuredVerse, progress,
                        { chapter, verse, resume ->
                            readerRequest = ReaderRequest(chapter, verse, resume)
                        },
                        { destination = Destination.CHAPTERS })

                    Destination.CHAPTERS -> ChapterList(
                        Modifier.padding(padding),
                        chapters
                    ) { readerRequest = ReaderRequest(it) }

                    Destination.SAVED -> Saved(
                        Modifier.padding(padding),
                        bookmarks,
                        highlights,
                        chapters,
                        repository
                    ) { chapter, verse -> readerRequest = ReaderRequest(chapter, verse) }

                    Destination.MORE -> More(Modifier.padding(padding), dark, actions) {
                        dark = !dark; storage.write(
                        StorageKeys.Theme,
                        if (dark) "dark" else "light"
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialBottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    Surface(color = Ink, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Destination.entries.forEach { item ->
                val active = selected == item
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onSelect(item) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(width = if (active) 28.dp else 5.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(if (active) Gold else Color.White.copy(alpha = .24f))
                    )
                    Text(
                        label(item),
                        color = if (active) Color.White else Color.White.copy(alpha = .6f),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
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
    open: (Chapter, Int?, Boolean) -> Unit,
    viewAll: () -> Unit
) = LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    item { Hero { chapters.getOrNull(1)?.let { open(it, null, false) } } }
    item {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Eyebrow("TODAY’S CONTEMPLATION"); Text(
            featuredVerse?.let { "A teaching from ${chapters.firstOrNull { chapter -> chapter.number == it.chapter }?.title.orEmpty()}" }
                ?: "A new teaching each day",
            fontFamily = FontFamily.Serif,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 7.dp)); Text(
            featuredVerse?.preferredTranslation().orEmpty().ifBlank { "Loading today’s verse…" },
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        ); TextButton(
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
                    open(
                        chapter,
                        null,
                        true
                    )
                }
            }
        }
    }
    item {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("EXPLORE"); Text(
                    "The eighteen paths",
                    fontFamily = FontFamily.Serif,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold
                )
                }; Text(
                "View all",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { viewAll() }
                    .padding(8.dp)
            )
            }; LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 14.dp)
        ) { items(chapters.take(6)) { c -> ChapterTile(c) { open(c, null, false) } } }
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

@Composable
private fun Hero(open: () -> Unit) =
    Box(Modifier.fillMaxWidth().height(410.dp).clickable { open() }) {
        Image(
            painterResource(Res.drawable.hero_manuscript),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Ink.copy(alpha = .08f),
                        Color.Transparent,
                        Ink.copy(alpha = .86f)
                    )
                )
            )
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(Gold)
                ); Text(
                "  GĪTĀ  ·  DAILY READER",
                color = Color.White,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            }
            Spacer(Modifier.weight(1f))
            Text("श्रीमद्भगवद्गीता", color = Gold, fontFamily = scriptureFont(), fontSize = 18.sp)
            Text(
                "Wisdom for\nthe inner life",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontSize = 42.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 5.dp)
            )
            Text(
                "18 chapters  ·  700 verses",
                color = Color.White.copy(alpha = .72f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

@Composable
private fun ContinueCard(chapter: Chapter, progress: ReadingProgress, open: () -> Unit) = Surface(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp))
        .clickable { open() }, color = Ink
) {
    val percent = (progress.verse * 100 / chapter.verseCount.coerceAtLeast(1)).coerceIn(1, 100)
    Row(
        Modifier.padding(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(Saffron),
            contentAlignment = Alignment.Center
        ) {
            Text(
                progress.verse.toString(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }; Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
        Text(
            "CONTINUE READING",
            color = Gold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ); Text(
        chapter.title,
        color = Color.White,
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 4.dp)
    ); Text(
        "Chapter ${chapter.number} · Verse ${progress.verse} · $percent% complete",
        color = Color.White.copy(alpha = .62f),
        fontSize = 13.sp
    )
    }; Text("→", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
private fun ChapterTile(c: Chapter, open: () -> Unit) = Surface(
    Modifier.width(164.dp).height(190.dp).clip(RoundedCornerShape(24.dp)).clickable { open() },
    color = if (c.number % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
) {
    Column(Modifier.padding(18.dp)) {
        Text(
            c.number.toString().padStart(2, '0'),
            color = if (c.number % 2 == 0) Gold else Saffron,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light
        ); Spacer(Modifier.weight(1f)); Text(
        c.sanskrit,
        fontFamily = scriptureFont(),
        color = if (c.number % 2 == 0) Color.White.copy(.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        maxLines = 1
    ); Text(
        c.title,
        color = if (c.number % 2 == 0) Color.White else MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Serif,
        fontSize = 19.sp,
        lineHeight = 21.sp,
        maxLines = 2,
        modifier = Modifier.padding(top = 5.dp)
    )
    }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.secondary,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.8.sp
)

@Composable
private fun QuoteCard(v: GitaVerse, open: () -> Unit) = Surface(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { open() },
    color = MaterialTheme.colorScheme.surface
) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Eyebrow("VERSE ${v.reference}"); Text(
        v.devanagari,
        fontFamily = scriptureFont(),
        fontSize = 22.sp,
        lineHeight = 35.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 18.dp)
    ); HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(.5f)); Text(
        v.preferredTranslation(),
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        lineHeight = 27.sp,
        modifier = Modifier.padding(top = 18.dp)
    )
    }
}

@Composable
private fun ChapterList(modifier: Modifier, chapters: List<Chapter>, open: (Chapter) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = chapters.filter {
        query.isBlank() || it.title.contains(
            query,
            true
        ) || it.sanskrit.contains(query) || it.number.toString() == query.trim()
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                Modifier.background(Ink).statusBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 30.dp)
            ) {
                Eyebrow("THE SACRED DIALOGUE"); Text(
                "Eighteen paths,\none destination.",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontSize = 35.sp,
                lineHeight = 39.sp,
                modifier = Modifier.padding(top = 8.dp)
            ); OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(top = 22.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        "Chapter, teaching or number",
                        color = Color.White.copy(.55f)
                    )
                },
                leadingIcon = { Text("⌕", color = Gold, fontSize = 23.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Color.White.copy(.28f)
                ),
                shape = RoundedCornerShape(18.dp)
            )
            }
        }
        items(filtered) { c ->
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(22.dp))
                    .clickable { open(c) },
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(if (c.number % 3 == 0) Clay else if (c.number % 2 == 0) Saffron else Ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            c.number.toString(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }; Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                        c.sanskrit,
                        fontFamily = scriptureFont(),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp
                    ); Text(
                    c.title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                ); Text(
                    "${c.verses} verses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }; Text("↗", color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp)
                }
            }
        }
        if (filtered.isEmpty()) item {
            Text(
                "No chapters match “$query”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
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
        storage.write(
            StorageKeys.LastChapter,
            chapter.number.toString()
        )
    }
    LaunchedEffect(content, request) {
        if (content == null || positionRestored) return@LaunchedEffect
        val stored = storage.progressFor(chapter.number, chapter.verseCount)
        val targetIndex = when {
            request.verse != null -> verses.indexOfFirst { it.number == request.verse }
                .takeIf { it >= 0 }?.plus(1) ?: 1

            request.resume -> stored.listIndex.coerceIn(1, verses.size)
            else -> 0
        }
        listState.scrollToItem(
            targetIndex,
            if (request.resume && request.verse == null) stored.scrollOffset else 0
        )
        positionRestored = true
    }
    LaunchedEffect(positionRestored, verses) {
        if (!positionRestored || verses.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
            delay(250)
            val verseIndex = index.coerceIn(1, verses.size)
            val progress =
                ReadingProgress(chapter.number, verseIndex, offset, verses[verseIndex - 1].number)
            storage.write(StorageKeys.LastChapter, chapter.number.toString())
            storage.write(StorageKeys.ReadingIndexPrefix + chapter.number, verseIndex.toString())
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
                        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(back) { Text("←", color = Color.White, fontSize = 24.sp) }; Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "CHAPTER ${chapter.number}",
                        color = Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ); Text(
                    chapter.title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp
                )
                }; Spacer(Modifier.width(48.dp))
                }
            }
        }) { padding ->
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
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.secondary
                    ); Text(
                    chapter.summary,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                ); ReadingModeToggle(
                    mode,
                    Modifier.fillMaxWidth().padding(top = 18.dp)
                ) { selected ->
                    mode = selected; storage.write(
                    StorageKeys.ReadingMode,
                    selected.name
                )
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
                    v,
                    mode,
                    v.reference in bookmarks,
                    v.reference in highlights,
                    playingId == v.reference,
                    { toggle(v.reference) },
                    { toggleHighlight(v.reference) }) {
                    if (playingId == v.reference) {
                        audioPlayer.stop(); playingId = null
                    } else scope.launch {
                        audioPlayer.stop(); playingId = v.reference
                        audioPlayer.play(v.reference, repository.audio(v)) { playingId = null }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun EmptyChapter(chapter: Chapter) = OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Text not installed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Chapter ${chapter.number} metadata is ready, but its verses require the licensed or public-domain edition selected for release.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun VerseCard(
    v: GitaVerse,
    mode: ReadingMode,
    saved: Boolean,
    highlighted: Boolean,
    playing: Boolean,
    save: () -> Unit,
    highlight: () -> Unit,
    audio: () -> Unit
) {
    var translationIndex by remember(v.id) { mutableIntStateOf(0) }
    var commentaryIndex by remember(v.id) { mutableIntStateOf(0) }
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
                        fontSize = 23.sp,
                        lineHeight = 38.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    )
                    Text(
                        v.transliteration,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (mode == ReadingMode.FULL) {
                    Label("WORD BY WORD"); Text(
                        v.wordMeanings,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
                if (mode != ReadingMode.SHLOKA_ONLY) {
                    Label("TRANSLATIONS")
                    ContentPager(
                        v.translations,
                        translationIndex,
                        { translationIndex = it },
                        highlighted
                    )
                    if (v.commentaries.isNotEmpty()) {
                        Label("COMMENTARY"); ContentPager(
                            v.commentaries,
                            commentaryIndex,
                            { commentaryIndex = it },
                            false
                        )
                    }
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
    highlighted: Boolean
) {
    if (items.isEmpty()) {
        Text(
            "No text available for this section.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ); return
    }
    val safeIndex = index.coerceIn(items.indices);
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
                ); Text(
                "${safeIndex + 1} / ${items.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            }
            Text(
                item.text,
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton({ select(if (safeIndex == 0) items.lastIndex else safeIndex - 1) }) {
                    Text(
                        "←"
                    )
                }; TextButton({ select(if (safeIndex == items.lastIndex) 0 else safeIndex + 1) }) {
                Text(
                    "NEXT  →",
                    fontWeight = FontWeight.Bold
                )
            }
            }
        }
    }
}

@Composable
private fun ReadingModeToggle(
    selected: ReadingMode,
    modifier: Modifier = Modifier,
    select: (ReadingMode) -> Unit
) = Surface(
    modifier, color = Ink, shape = RoundedCornerShape(20.dp)
) {
    Row(Modifier.padding(5.dp)) {
        listOf(
            ReadingMode.FULL to "Complete",
            ReadingMode.SHLOKA_ONLY to "Sanskrit",
            ReadingMode.TRANSLATION_ONLY to "Meaning"
        ).forEach { (mode, label) ->
            val active = selected == mode
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (active) Saffron else Color.Transparent)
                    .clickable { if (!active) select(mode) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Color.White.copy(alpha = .65f),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
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
    open: (Chapter, Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) };
    val ids = if (tab == 0) bookmarks else highlights;
    val title = if (tab == 0) "bookmark" else "highlight"
    val markedVerses by produceState<List<GitaVerse>>(emptyList(), ids) {
        value = ids.mapNotNull { ref ->
            val parts = ref.split('.');
            val chapter = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null;
            val number =
                parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null; repository.chapter(
            chapter
        ).verses.firstOrNull { it.number == number }
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
            items(
                markedVerses,
                key = { it.id }) { verse ->
                QuoteCard(verse) {
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
    actions: PlatformActions,
    changeTheme: () -> Unit
) = LazyColumn(
    modifier.fillMaxSize(),
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
        MoreSection("APPEARANCE", "Choose your reading atmosphere.") {
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
        }
    }
    item {
        MoreSection("THE COMPLETE LIBRARY", "The full dialogue, available without a connection.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryStat(
                        "18",
                        "chapters",
                        Modifier.weight(1f)
                    ); LibraryStat("701", "verses", Modifier.weight(1f)); LibraryStat(
                    "701",
                    "recitations",
                    Modifier.weight(1f)
                )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryStat(
                        "4,907",
                        "translations",
                        Modifier.weight(1f)
                    ); LibraryStat("11,186", "commentaries", Modifier.weight(1f)); LibraryStat(
                    "3",
                    "languages",
                    Modifier.weight(1f)
                )
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
                DetailLine("Edition", "Bundled multilingual JSON corpus")
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
                "BHAGAVAD GITA READER  ·  VERSION 1.0",
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
            ); Text(
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
private fun GitaVerse.preferredTranslation(): String =
    translations.firstOrNull { it.language.equals("english", true) }?.text
        ?: translations.firstOrNull()?.text.orEmpty()

private fun label(item: Destination) = when (item) {
    Destination.HOME -> "Home"; Destination.CHAPTERS -> "Chapters"; Destination.SAVED -> "Saved"; Destination.MORE -> "More"
}

private fun icon(item: Destination) = when (item) {
    Destination.HOME -> "⌂"; Destination.CHAPTERS -> "☷"; Destination.SAVED -> "☆"; Destination.MORE -> "•••"
}
