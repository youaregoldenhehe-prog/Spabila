package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class CuevanaProvider : MainAPI() {
    override var mainUrl = "https://cue-vana3.org"
    override var name = "Cuevana"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair(mainUrl, "Recientemente actualizadas"),
            Pair("$mainUrl/estrenos/", "Estrenos"),
        )
        
        try {
            val series = app.get("$mainUrl/serie", timeout = 120).document.select("section.home-series li")
                .map {
                    val title = it.selectFirst("h2.Title")!!.text()
                    val poster = it.selectFirst("img.lazy")!!.attr("data-src")
                    val url = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title, url, this.name, TvType.TvSeries, poster, null, null, null
                    )
                }
            items.add(HomePageList("Series", series))
        } catch (e: Exception) { }

        urls.apmap { (url, name) ->
            try {
                val soup = app.get(url).document
                val home = soup.select("section li.xxx.TPostMv").map {
                    val title = it.selectFirst("h2.Title")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    val poster = it.selectFirst("img.lazy")!!.attr("data-src")
                    
                    if (link.contains("/pelicula/")) {
                        MovieSearchResponse(title, link, this.name, TvType.Movie, poster, null)
                    } else {
                        TvSeriesSearchResponse(title, link, this.name, TvType.TvSeries, poster, null, null)
                    }
                }
                items.add(HomePageList(name, home))
            } catch (e: Exception) { }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select("li.xxx.TPostMv").map {
            val title = it.selectFirst("h2.Title")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img.lazy")!!.attr("data-src")
            if (href.contains("/serie/")) {
                TvSeriesSearchResponse(title, href, this.name, TvType.TvSeries, image, null, null)
            } else {
                MovieSearchResponse(title, href, this.name, TvType.Movie, image, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document
        val title = soup.selectFirst("h1.Title")!!.text()
        val description = soup.selectFirst(".Description p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".movtv-info div.Image img")!!.attr("data-src")
        
        val episodes = soup.select(".all-episodes li.TPostMv article").map { li ->
            val href = li.select("a").attr("href")
            val epThumb = li.selectFirst("div.Image img")?.attr("data-src") ?: ""
            val seasonid = li.selectFirst("span.Year")!!.text().split("x")
            Episode(
                href, null, seasonid.getOrNull(0)?.toIntOrNull(), seasonid.getOrNull(1)?.toIntOrNull(), fixUrl(epThumb)
            )
        }

        val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries
        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    data class Femcuevana(@JsonProperty("url") val url: String)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div.TPlayer.embed_div iframe").apmap {
            val iframe = fixUrl(it.attr("data-src"))
            if (iframe.contains("api.cue-vana3.org/fembed/")) {
                val key = iframe.split("h=").lastOrNull()
                if (key != null) {
                    try {
                        val response = app.post(
                            "https://api.cue-vana3.org/fembed/api.php",
                            data = mapOf("h" to key),
                            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                        ).text
                        val json = parseJson<Femcuevana>(response)
                        loadExtractor(json.url, data, subtitleCallback, callback)
                    } catch (e: Exception) { }
                }
            }
        }
        return true
    }
}
