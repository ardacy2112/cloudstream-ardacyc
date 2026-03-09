package com.ardacy2112

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.extractors.loadExtractor
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.fixUrl
import java.net.URLEncoder
import org.jsoup.nodes.Element

class SelcukflixProvider : MainAPI() {
    override var mainUrl = "https://selcukflix.net"
    override var name = "Selcukflix"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(searchUrl, referer = mainUrl).document

        return document.select("article, .item, .movie, .tvshow, .result-item")
            .mapNotNull(::toSearchResponse)
            .distinctBy { it.url }
    }

    override suspend fun load(url: String) = app.get(url, referer = mainUrl).document.let { document ->
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

        val episodeLinks = document.select(
            "a[href*=/bolum/], a[href*=sezon], .episode a[href], .episodes a[href], .episodios a[href]"
        )
            .mapNotNull { element ->
                val href = element.attr("href").trim()
                if (href.isBlank()) null else Episode(data = fixUrl(href), name = element.text().trim())
            }
            .distinctBy { it.data }

        if (episodeLinks.isNotEmpty() || url.contains("/dizi")) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeLinks.ifEmpty { listOf(Episode(url, "1. Bolum")) }) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(data, referer = mainUrl).document

        val links = linkedSetOf<String>()
        document.select("iframe[src], iframe[data-src], a[href*='http']")
            .mapNotNull { element ->
                element.attr("data-src").ifBlank { element.attr("src").ifBlank { element.attr("href") } }
            }
            .map(String::trim)
            .filter { it.startsWith("http") || it.startsWith("//") }
            .forEach { links.add(fixUrl(it)) }

        Regex("""https?:\\/\\/[^\s"'<>\\]+""")
            .findAll(document.html())
            .map { it.value.replace("\\/", "/") }
            .filterNot { it.contains(mainUrl) }
            .forEach { links.add(it) }

        var found = false
        links.forEach { link ->
            loadExtractor(link, data, subtitleCallback, callback)
            found = true
        }
        return found
    }

    private fun toSearchResponse(element: Element): SearchResponse? {
        val link = element.selectFirst("a[href]")?.attr("href")?.trim().orEmpty()
        if (link.isBlank()) return null

        val title = element.selectFirst("h1, h2, h3, h4, a[title]")?.text()?.trim()
            ?: element.selectFirst("a[title]")?.attr("title")?.trim()
            ?: return null

        val poster = element.selectFirst("img[data-src], img[src], img[data-original]")
            ?.let {
                it.attr("data-src")
                    .ifBlank { it.attr("data-original") }
                    .ifBlank { it.attr("src") }
                    .trim()
            }
            ?.takeIf { it.isNotBlank() }
            ?.let(::fixUrl)

        val isSeries = link.contains("/dizi") || element.text().contains("dizi", ignoreCase = true)
        val normalizedLink = fixUrl(link)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, normalizedLink, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, normalizedLink, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }
}
