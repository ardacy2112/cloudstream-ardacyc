package com.ardacy2112

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.mainPageOf
import com.lagradost.cloudstream3.utils.newHomePageResponse
import com.lagradost.cloudstream3.utils.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.newTvSeriesSearchResponse
import java.net.URLEncoder
import org.jsoup.nodes.Element

class SelcukflixProvider : MainAPI() {
    override var mainUrl = "https://selcukflix.net"
    override var name = "Selcukflix"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/filmler/page/" to "Filmler",
        "$mainUrl/diziler/page/" to "Diziler",
        "$mainUrl" to "Son Eklenenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.data.endsWith("/page/") -> "${request.data}$page/"
            page == 1 -> request.data
            else -> "${request.data.trimEnd('/')}/page/$page/"
        }

        val document = app.get(url, referer = mainUrl).document
        val results = document.select("article, .item, .movie, .tvshow, .result-item, .TPostMv")
            .mapNotNull(::toSearchResponse)
            .distinctBy { it.url }

        return newHomePageResponse(request.name, results, hasNext = results.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(searchUrl, referer = mainUrl).document
        return document.select("article, .item, .movie, .tvshow, .result-item, .TPostMv")
            .mapNotNull(::toSearchResponse)
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = mainUrl).document

        val title = document.selectFirst("h1, meta[property=og:title]")
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.text() }
            ?.trim()
            ?: return null

        val poster = document.selectFirst("meta[property=og:image], .poster img, img")
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.attr("src") }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fixUrl)

        val description = document.selectFirst("meta[property=og:description], .entry-content p, .description p")
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.text() }
            ?.trim()

        val year = Regex("""(19|20)\d{2}""").find(document.text())?.value?.toIntOrNull()
        val tags = document.select("a[rel=tag], .sgeneros a, .genres a").map { it.text().trim() }.filter { it.isNotBlank() }

        val episodes = document.select("a[href*=/bolum/], a[href*=sezon], .episode a[href], .episodes a[href], .episodios a[href]")
            .mapNotNull { it.attr("href").takeIf(String::isNotBlank)?.let(::fixUrl) }
            .distinct()
            .mapIndexed { index, epUrl ->
                newEpisode(epUrl) {
                    this.name = "Bolum ${index + 1}"
                }
            }

        val isSeries = episodes.isNotEmpty() || url.contains("/dizi")
        return if (isSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = mainUrl).document
        val links = linkedSetOf<String>()

        document.select("iframe[src], iframe[data-src], a[href*='http']")
            .mapNotNull { element ->
                element.attr("data-src").ifBlank { element.attr("src").ifBlank { element.attr("href") } }
            }
            .map(String::trim)
            .filter { it.startsWith("http") || it.startsWith("//") }
            .map(::fixUrl)
            .forEach(links::add)

        Regex("""https?:\\/\\/[^\s"'<>\\]+""")
            .findAll(document.html())
            .map { it.value.replace("\\/", "/") }
            .filterNot { it.contains(mainUrl) }
            .forEach(links::add)

        links.forEach { link ->
            loadExtractor(link, data, subtitleCallback, callback)
        }
        return links.isNotEmpty()
    }

    private fun toSearchResponse(element: Element): SearchResponse? {
        val href = element.selectFirst("a[href]")?.attr("href")?.trim().orEmpty()
        if (href.isBlank()) return null

        val title = element.selectFirst("h1, h2, h3, h4")?.text()?.trim()
            ?: element.selectFirst("a[title]")?.attr("title")?.trim()
            ?: return null

        val poster = element.selectFirst("img[data-src], img[data-original], img[src]")
            ?.let {
                it.attr("data-src")
                    .ifBlank { it.attr("data-original") }
                    .ifBlank { it.attr("src") }
                    .trim()
            }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fixUrl)

        val url = fixUrl(href)
        val isSeries = url.contains("/dizi") || element.text().contains("dizi", ignoreCase = true)
        return if (isSeries) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) { this.posterUrl = poster }
        }
    }
}
