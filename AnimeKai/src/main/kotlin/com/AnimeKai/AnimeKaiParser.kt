package com.AnimeKai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.CoverImage
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.LikePageInfo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.RecommendationConnection
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.SeasonNextAiringEpisode
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Title
import org.json.JSONObject


@JsonIgnoreProperties(ignoreUnknown = true)
data class Image(
    @param:JsonProperty("coverType") val coverType: String?,
    @param:JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Episode(
    @param:JsonProperty("episode") val episode: String?,
    @param:JsonProperty("airdate") val airdate: String?,
    @param:JsonProperty("airDateUtc") val airDateUtc: String?,
    @param:JsonProperty("runtime") val runtime: Int?,     // Keeping only one field
    @param:JsonProperty("image") val image: String?,
    @param:JsonProperty("title") val title: Map<String, String>?,
    @param:JsonProperty("overview") val overview: String?,
    @param:JsonProperty("rating") val rating: String?,
    @param:JsonProperty("finaleType") val finaleType: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaMappings(
    @param:JsonProperty("themoviedb_id") val themoviedbId: Int? = null,
    @param:JsonProperty("thetvdb_id") val thetvdbId: Int? = null,
    @param:JsonProperty("imdb_id") val imdbId: String? = null,
    @param:JsonProperty("mal_id") val malId: Int? = null,
    @param:JsonProperty("anilist_id") val anilistId: Int? = null,
    @param:JsonProperty("kitsu_id") val kitsuid: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @param:JsonProperty("titles") val titles: Map<String, String>?,
    @param:JsonProperty("images") val images: List<Image>?,
    @param:JsonProperty("episodes") val episodes: Map<String, Episode>?,
    @param:JsonProperty("mappings") val mappings: MetaMappings? = null
)

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}

private val apiUrl = "https://graphql.anilist.co"

private val headerJSON =
    mapOf("Accept" to "application/json", "Content-Type" to "application/json")
suspend fun anilistAPICall(query: String): AnilistAPIResponse {
    val data = mapOf("query" to query)
    val test = app.post(apiUrl, headers = headerJSON, data = data)
    val res =
        test.parsedSafe<AnilistAPIResponse>()
            ?: throw Exception("Unable to fetch or parse Anilist api response")
    return res
}

data class AnilistAPIResponse(
    @param:JsonProperty("data") val data: AnilistData,
) {
    data class AnilistData(
        @param:JsonProperty("Page") val page: AnilistPage?,
        @param:JsonProperty("Media") val media: anilistMedia?,
    ) {
        data class AnilistPage(
            @param:JsonProperty("pageInfo") val pageInfo: LikePageInfo,
            @param:JsonProperty("media") val media: List<Media>,
        )
    }

    data class anilistMedia(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("startDate") val startDate: StartDate,
        @param:JsonProperty("episodes") val episodes: Int?,
        @param:JsonProperty("title") val title: Title,
        @param:JsonProperty("season") val season: String?,
        @param:JsonProperty("genres") val genres: List<String>,
        @param:JsonProperty("averageScore") val averageScore: Int,
        @param:JsonProperty("status") val status: String,
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("coverImage") val coverImage: CoverImage,
        @param:JsonProperty("bannerImage") val bannerImage: String?,
        @param:JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @param:JsonProperty("airingSchedule") val airingSchedule: AiringScheduleNodes?,
        @param:JsonProperty("recommendations") val recommendations: RecommendationConnection?,
        @param:JsonProperty("externalLinks") val externalLinks: List<ExternalLink>?,
        @param:JsonProperty("format") val format: String?,
    ) {
        data class StartDate(@param:JsonProperty("year") val year: Int)

        data class AiringScheduleNodes(
            @param:JsonProperty("nodes") val nodes: List<SeasonNextAiringEpisode>?
        )

        data class ExternalLink(
            val site: String,
            val url: String
        )

        fun getCoverImage(): String? {
            return coverImage.extraLarge ?: coverImage.large ?: coverImage.medium
        }
    }

    data class Media(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("idMal") val idMal: Int?,
        @param:JsonProperty("season") val season: String?,
        @param:JsonProperty("seasonYear") val seasonYear: Int,
        @param:JsonProperty("format") val format: String?,
        @param:JsonProperty("averageScore") val averageScore: Int,
        @param:JsonProperty("episodes") val episodes: Int,
        @param:JsonProperty("title") val title: Title,
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("coverImage") val coverImage: CoverImage,
        @param:JsonProperty("synonyms") val synonyms: List<String>,
        @param:JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )
}

suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val url = if (type == TvType.AnimeMovie)
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    else
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

    // Language match
    var svgFallback: JSONObject? = null

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        val p = path(logo)
        if (p.isBlank()) continue

        val l = logo.optString("iso_639_1").trim().lowercase()
        if (l == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    // Highest voted fallback
    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(o: JSONObject) = o.optDouble("vote_average", 0.0) > 0 && o.optInt("vote_count", 0) > 0

    fun better(a: JSONObject?, b: JSONObject): Boolean {
        if (a == null) return true
        val aAvg = a.optDouble("vote_average", 0.0)
        val aCnt = a.optInt("vote_count", 0)
        val bAvg = b.optDouble("vote_average", 0.0)
        val bCnt = b.optInt("vote_count", 0)
        return bAvg > aAvg || (bAvg == aAvg && bCnt > aCnt)
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }

    // No language match & no voted logos
    return null
}