package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
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
        
        // Sección de Series
        try {
            val series = app.get("$mainUrl/serie", timeout = 120).document.select("section.home-series li")
                .map {
                    val title = it.selectFirst("h2.Title")!!.text()
                    val poster = it.selectFirst("img.lazy")!!.attr("data-src")
                    val url = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title,
                        url,
                        this.name,
                        TvType.TvSeries,
                        poster,
                        null,
                        null,
                        null,
                    )
                }
            items.add(HomePageList("Series", series))
        } catch (e: Exception) { }

        // Sección de Películas y Estrenos
        urls.apmap { (url, name) ->
            try {
                val soup = app.get(url).document
                val home = soup.select("section li.xxx.TPostMv").map {
                    val title = it.selectFirst("h2.Title")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    val poster = it.selectFirst("img.lazy")!!.attr("data-src")
                    
                    if (link.contains("/pelicula/")) {
                        MovieSearchResponse(
                            title, link, this.name, TvType.Movie, poster, null
                        )
                    } else {
                        TvSeriesSearchResponse(
                            title, link, this.name, TvType.TvSeries, poster, null, null
                        )
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
            val isSerie = href.contains("/serie/")

            if (isSerie) {
                TvSeriesSearchResponse(title, href, this.name, TvType.TvSeries, image, null, null)
            } else {
                MovieSearchResponse(title, href, this.name, TvType.Movie, image, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
