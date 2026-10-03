package com.jarvis.agent.skills

import com.jarvis.agent.AgentAction

class MediaSkill : Skill {
    override val id: String = "smart_media"
    override val name: String = "Media Intelligence Skill"
    override val description: String = "Handles ambiguous and personalized music, video, and comedy requests."
    override val triggers: List<String> = listOf(
        "song", "gana", "music", "video", "comedy", "standup", "stand-up",
        "play", "chalao", "bajao", "sunao", "trending", "romantic", "sad song", "energetic",
        "next song", "agla gaana", "next track", "skip", "skip song",
        "previous song", "previous track", "pichla gaana", "pichhla gaana", "pichla", "pichhla", "prev song", "prev track", "back track",
        "resume music", "gaana chalao", "continue music", "pause music", "pause",
        "spotify", "lyrics", "song lyrics", "gaane ke bol", "youtube", "history"
    )

    override suspend fun canHandle(goal: String, context: SkillContext): Boolean {
        val lower = goal.lowercase().trim()
        if (lower.contains("play store") || lower.contains("playstore") || lower.contains("google play")) {
            return false
        }
        return triggers.any { lower.contains(it) }
    }

    override suspend fun execute(goal: String, context: SkillContext): SkillResult {
        val lower = goal.lowercase().trim()

        // 0. Explicit YouTube Session Actions
        if (lower.contains("youtube") && (lower.contains("history") || lower.contains("purana") || lower.contains("list"))) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("YOUTUBE_PLAY", mapOf("action" to "history")),
                explanation = "Retrieving YouTube session history"
            )
        }
        if (lower.contains("youtube") && (lower.contains("current") || lower.contains("kya") || lower.contains("status") || lower.contains("playing"))) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("YOUTUBE_PLAY", mapOf("action" to "current")),
                explanation = "Retrieving current YouTube video session info"
            )
        }
        if (lower.contains("youtube") && (lower.contains("previous") || lower.contains("pichla") || lower.contains("prev") || lower.contains("back"))) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("YOUTUBE_PLAY", mapOf("action" to "previous")),
                explanation = "Playing previous video from YouTube session history"
            )
        }
        if (lower.contains("youtube") && lower.contains("pause")) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("YOUTUBE_PLAY", mapOf("action" to "pause")),
                explanation = "Pausing YouTube session playback"
            )
        }

        // 1. Hardware media playback controls
        if (lower.contains("next") || lower.contains("skip") || lower.contains("agla")) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("MEDIA_CONTROL", mapOf("action" to "next")),
                explanation = "Skipping to next track"
            )
        }
        if (lower.contains("previous") || lower.contains("prev") || lower.contains("pichla") || lower.contains("pichhla")) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("MEDIA_CONTROL", mapOf("action" to "previous")),
                explanation = "Returning to previous track"
            )
        }
        if (lower.contains("resume") || lower.contains("continue") || (lower.contains("wapas") && lower.contains("chalao"))) {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("MEDIA_CONTROL", mapOf("action" to "play")),
                explanation = "Resuming playback"
            )
        }
        if (lower == "pause" || lower == "pause music" || lower == "gaana pause karo") {
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("MEDIA_CONTROL", mapOf("action" to "pause")),
                explanation = "Pausing playback"
            )
        }

        // 2. Lyrics retrieval (requires explicit "lyrics" or standalone word "bol" / "gaane ke bol")
        if (lower.contains("lyrics") || Regex("\\b(gaane\\s+ke\\s+)?bol\\b").containsMatchIn(lower)) {
            val songName = lower.replace(Regex("\\b(show|lyrics|for|of|gaane ke|bol|batao|dikhao|what are the)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
            return SkillResult(
                handled = true,
                proposedAction = AgentAction("WEB_SEARCH", mapOf("query" to "$songName lyrics")),
                explanation = "Searching web for lyrics: $songName"
            )
        }

        val prefs = context.userPreferences
        val failed = context.workingMemory.failedSteps

        // NEVER fabricate a personal preference: an artist/genre is used only if the
        // user actually stated/learned one, otherwise fall back to a neutral query.
        val favoriteArtist = prefs["favorite_artist"] ?: prefs["artist"]
        val favoriteGenre = prefs["favorite_genre"] ?: prefs["genre"] ?: "popular"
        val comedyPref = prefs["comedy_preference"]

        val query: String
        val explanation: String

        when {
            lower.contains("spotify") -> {
                val cleaned = lower.replace(Regex("\\b(play|spotify|on|pe|chalao|bajao)\\b", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("\\s+"), " ").trim()
                query = cleaned.ifBlank { if (favoriteArtist != null) "$favoriteArtist hits" else "popular playlist" }
                explanation = "Playing on Spotify: $query"
                return SkillResult(
                    handled = true,
                    proposedAction = AgentAction("SPOTIFY_PLAY", mapOf("query" to query)),
                    explanation = explanation
                )
            }
            lower.contains("comedy") || lower.contains("standup") || lower.contains("stand-up") || lower.contains("funny") -> {
                query = if (comedyPref != null) "$comedyPref video" else "latest standup comedy hindi"
                explanation = "Selected comedy content${if (comedyPref != null) " based on preference: $query" else " (neutral recommendation): $query"}"
            }
            lower.contains("trending") || lower.contains("latest") -> {
                query = "latest trending songs 2026"
                explanation = "Selected latest trending music"
            }
            lower.contains("sad") -> {
                query = if (favoriteArtist != null) "$favoriteArtist sad emotional songs" else "sad emotional songs hindi"
                explanation = "Selected sad song${if (favoriteArtist != null) " for preferred artist $favoriteArtist" else " (neutral)"}"
            }
            lower.contains("energetic") || lower.contains("party") -> {
                query = "high energy party dance songs hindi"
                explanation = "Selected energetic music"
            }
            lower.contains("favorite") || lower.contains("badiya") || lower.contains("accha") || lower.contains("koi") -> {
                query = when {
                    favoriteArtist != null -> "$favoriteArtist top hits"
                    failed.isEmpty() -> "trending hindi top songs"
                    else -> "$favoriteGenre top music playlist"
                }
                explanation = if (favoriteArtist != null) "Personalized selection using preferred artist $favoriteArtist" else "Neutral recommendation (no stored artist preference)"
            }
            else -> {
                val cleaned = lower.replace("play", "").replace("chalao", "").replace("bajao", "").replace("sunao", "").trim()
                query = cleaned.ifBlank { if (favoriteArtist != null) "$favoriteArtist popular songs" else "popular hindi songs" }
                explanation = "Media query: $query"
            }
        }

        val candidates = when {
            lower.contains("comedy") || lower.contains("standup") || lower.contains("stand-up") || lower.contains("funny") -> {
                val base = mutableListOf(query, "trending comedy clips hindi", "best standup comedy specials hindi")
                if (comedyPref != null) base.add("$comedyPref top video")
                base.distinct()
            }
            else -> {
                val base = mutableListOf(query, "latest hindi trending songs 2026")
                if (favoriteArtist != null) base.add("$favoriteArtist top tracks")
                base.add("$favoriteGenre hits playlist")
                base.distinct()
            }
        }

        return SkillResult(
            handled = true,
            proposedAction = AgentAction(
                type = "YOUTUBE_PLAY",
                params = mapOf("query" to query),
                expectedOutcome = "Opening media for: $query"
            ),
            explanation = explanation,
            candidateQueries = candidates
        )
    }
}
