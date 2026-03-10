package com.ardacy2112

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class SelcukflixProvider : MainAPI() {

    override var mainUrl = "https://selcukflix.net"
    override var name = "Selcukflix"
    override var lang = "tr"

    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override var mainPage = listOf(
        MainPageData("$mainUrl", "Son Eklenenler"),
        MainPageData("$mainUrl/film", "Filmler"),
        MainPageData("$mainUrl/dizi", "Diziler")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val document = app.get(request.data).document

        val home = document.select("article, .poster, .ml-item")
            .mapNotNull { toSearch(it) }

        return newHomePageResponse(
            listOf(HomePageList(request.name, home))
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val url = "$mainUrl/?s=${URLEncoder.encode(query,"UTF-8")}"

        val document = app.get(url).document

        return document.select("article, .poster, .ml-item")
            .mapNotNull { toSearch(it) }
    }

    private fun toSearch(element: Element): SearchResponse? {

        val link = element.selectFirst("a") ?: return null

        val href = link.attr("href")

        val title = link.attr("title")
            .ifBlank { element.text() }

        val poster = element.selectFirst("img")?.attr("src")

        val isSeries = href.contains("dizi")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title = doc.selectFirst("h1")!!.text()

        val poster = doc.selectFirst("img")?.attr("src")

        val desc = doc.selectFirst("p")?.text()

        val iframe = doc.selectFirst("iframe")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, iframe) {
            posterUrl = poster
            plot = desc
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        loadExtractor(data, mainUrl, subtitleCallback, callback)

        return true
    }
}
