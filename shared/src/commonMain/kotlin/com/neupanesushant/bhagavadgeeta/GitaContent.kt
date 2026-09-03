package com.neupanesushant.bhagavadgeeta

import bhagavadgeeta.shared.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Serializable
data class GitaChapterSummary(
    val number: Int,
    val name: String,
    val meaning: String,
    val transliteratedName: String,
    val summary: String,
    val verseCount: Int,
)

@Serializable
data class GitaText(
    val id: Int,
    val sourceAuthorId: Int,
    val author: String,
    val language: String,
    val text: String,
)

@Serializable
data class GitaVerse(
    val id: Int,
    val chapter: Int,
    val number: Int,
    val title: String,
    val devanagari: String,
    val transliteration: String,
    val wordMeanings: String,
    val audioPath: String,
    val translations: List<GitaText>,
    val commentaries: List<GitaText>,
) { val reference: String get() = "$chapter.$number" }

@Serializable
data class GitaChapter(
    val number: Int,
    val name: String,
    val meaning: String,
    val transliteratedName: String,
    val summary: String,
    val verseCount: Int,
    val verses: List<GitaVerse>,
)

interface GitaRepository {
    suspend fun catalog(): List<GitaChapterSummary>
    suspend fun chapter(number: Int): GitaChapter
    suspend fun audio(verse: GitaVerse): ByteArray
}

@OptIn(ExperimentalResourceApi::class)
class BundledGitaRepository : GitaRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<Int, GitaChapter>()

    override suspend fun catalog(): List<GitaChapterSummary> = json.decodeFromString(
        Res.readBytes("files/content/catalog.json").decodeToString()
    )

    override suspend fun chapter(number: Int): GitaChapter = cache.getOrPut(number) {
        json.decodeFromString(
            Res.readBytes("files/content/chapter_${number.toString().padStart(2, '0')}.json").decodeToString()
        )
    }

    override suspend fun audio(verse: GitaVerse): ByteArray = Res.readBytes(verse.audioPath)
}
