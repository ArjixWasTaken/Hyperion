package com.hyperion.network.service

import com.hyperion.network.body.BrowseBody
import com.hyperion.network.body.NextBody
import com.hyperion.network.body.PlayerBody
import com.hyperion.network.body.SearchBody
import com.hyperion.network.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class InnerTubeService(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private lateinit var innerTubeApiKey: String
    private lateinit var innerTubeContext: ApiContext

    init {
        runBlocking { initApi() }
    }

    private suspend fun initApi() = withContext(Dispatchers.IO) {
        val html = httpClient.get(YOUTUBE_URL).bodyAsText()

        val (obj) = "ytcfg.set\\((.*?)\\);".toRegex(RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .elementAt(1)
            .destructured

        val data: InnerTubeData = json.decodeFromString(obj)

        innerTubeApiKey = data.innerTubeApiKey
        innerTubeContext = data.innerTubeContext.copy(
            client = data.innerTubeContext.client.copy(
                clientName = CLIENT_NAME,
                clientVersion = CLIENT_VERSION,
                platform = PLATFORM,
                clientFormFactor = FORM_FACTOR
            )
        )
    }

    private suspend inline fun <reified T> getBrowse(body: BrowseBody) = withContext(Dispatchers.IO) {
        httpClient.post("$API_URL/browse") {
            parameter("key", innerTubeApiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<T>()
    }

    suspend fun getRecommendations(): HttpResponse = getBrowse(
        BrowseBody(
            context = innerTubeContext,
            browseId = "FEwhat_to_watch"
        )
    )

    suspend fun getTrending(continuation: String?): ApiTrending = getBrowse(
        BrowseBody(
            context = innerTubeContext,
            browseId = "FEtrending",
            continuation = continuation
        )
    )

    suspend fun getChannel(id: String, params: String? = null): ApiChannel = getBrowse(
        BrowseBody(
            context = innerTubeContext,
            browseId = id,
            params = params
        )
    )

    suspend fun getSearchSuggestions(search: String): JsonElement = withContext(Dispatchers.IO) {
        val body = httpClient.get("https://suggestqueries-clients6.youtube.com/complete/search") {
            parameter("client", "youtube")
            parameter("ds", "yt")
            parameter("q", search)
        }.bodyAsText()

        json.parseToJsonElement(body.substringAfter("(").substringBeforeLast(")"))
    }

    suspend fun getPlayer(id: String): ApiPlayer = withContext(Dispatchers.IO) {
        httpClient.post("$API_URL/player") {
            parameter("key", innerTubeApiKey)
            contentType(ContentType.Application.Json)
            setBody(
                PlayerBody(
                    context = innerTubeContext,
                    videoId = id
                )
            )
        }.body()
    }

    suspend fun getNext(id: String, continuation: String?): ApiNext = withContext(Dispatchers.IO) {
        httpClient.post("$API_URL/next") {
            parameter("key", innerTubeApiKey)
            contentType(ContentType.Application.Json)
            setBody(
                NextBody(
                    context = innerTubeContext,
                    videoId = id,
                    continuation = continuation
                )
            )
        }.body()
    }

    suspend fun getSearchResults(query: String, continuation: String?): ApiSearch = withContext(Dispatchers.IO) {
        httpClient.post("$API_URL/search") {
            parameter("key", innerTubeApiKey)
            contentType(ContentType.Application.Json)
            setBody(
                SearchBody(
                    context = innerTubeContext,
                    query = query,
                    continuation = continuation
                )
            )
        }.body()
    }

    companion object {
        private const val YOUTUBE_URL = "https://www.youtube.com"
        private const val API_URL = "https://www.youtube.com/youtubei/v1"

        private const val CLIENT_NAME = "ANDROID"
        private const val CLIENT_VERSION = "17.11.37"
        private const val PLATFORM = "MOBILE"
        private const val FORM_FACTOR = "SMALL_FORM_FACTOR"

        fun getVideoThumbnail(id: String): String = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }
}

@Serializable
private data class InnerTubeData(
    @SerialName("INNERTUBE_API_KEY")
    val innerTubeApiKey: String,
    @SerialName("INNERTUBE_CONTEXT")
    val innerTubeContext: ApiContext
)