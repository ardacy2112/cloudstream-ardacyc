package com.ardacy2112

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import java.net.URLEncoder
import org.jsoup.nodes.Element

class SelcukflixProvider : MainAPI() {
    override var mainUrl = "https://selcukflix.net"
    override var name = "Selcukflix"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override var mainPage = listOf(
        MainPageData("$mainUrl", "Son Eklenenler"),
        MainPageData("$mainUrl/filmler", "Filmler"),
        MainPageData("$mainUrl/diziler", "Diziler")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data.trimEnd('/')}/page/$page/"
        val document = app.get(url, referer = mainUrl).document
        val results = document.select("article, .item, .movie, .tvshow, .result-item, .TPostMv")
            .mapNotNull(::toSearchResponse)
            .distinctBy { it.url }
        return newHomePageResponse(listOf(HomePageList(request.name, results)), hasNext = results.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(searchUrl, referer = mainUrl).document
        return document.select("article, .item, .movie, .tvshow, .result-item, .TPostMv")
            .mapNotNull(::toSearchResponse)
            .distinctBy { it.url }
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

        val url = if (href.startsWith("http")) href else "$mainUrl/${href.trimStart('/')}"
        val isSeries = url.contains("/dizi") || element.text().contains("dizi", ignoreCase = true)
        return if (isSeries) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }
}
