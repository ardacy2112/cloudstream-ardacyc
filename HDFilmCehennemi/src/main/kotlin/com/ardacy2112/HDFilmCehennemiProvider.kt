package com.ardacy2112

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

class HDFilmCehennemiProvider : MainAPI() {
    override var mainUrl = "https://hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }
}
