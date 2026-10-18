package org.qosp.notes.data.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.qosp.notes.BuildConfig
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

class GeminiService {

    private val apiService: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun generateText(
        conversationHistory: List<ChatMessage>,
        systemInstruction: String = SYSTEM_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("GEMINI_API_KEY is not configured. Please set your Gemini API key in the Secrets panel in AI Studio.")
            )
        }

        val contents = conversationHistory.map { msg ->
            Content(
                role = if (msg.isUser) "user" else "model",
                parts = listOf(Part(text = msg.text))
            )
        }

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                maxOutputTokens = 2048
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstruction))
            )
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I couldn't generate a response."
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val SYSTEM_PROMPT = """You are a smart, friendly AI assistant for Notepad (inspired by Google Keep).
You specialize in helping users organize their thoughts:
1. Generating actionable checklists (groceries, travel packing, daily tasks, project milestones, workout routines).
2. Brainstorming creative ideas, recipes, outlines, study guides, and tips.
3. Summarizing notes or expanding outlines into detailed content.

Formatting guidelines:
- When asked for a list or checklist, provide a clean Title on the very first line.
- Follow with bullet points starting with "-" or "- [ ]" for each distinct item.
- Keep the items punchy, practical, and clear so they can easily be added as checkbox items.
"""

        fun parseListFromText(text: String): Pair<String, List<String>> {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return "New List" to emptyList()

            var title = lines.first()
                .removePrefix("#").removePrefix("##").removePrefix("###")
                .removePrefix("**").removeSuffix("**")
                .trim()
            if (title.isBlank()) title = "New List"

            val itemRegex = Regex("""^(\s*[-*•]|\s*\[[ xX]?]|\s*\d+[\.\)])\s*(.*)$""")
            val items = mutableListOf<String>()

            for (line in lines.drop(1)) {
                val match = itemRegex.find(line)
                if (match != null) {
                    val item = match.groupValues[2].trim()
                    if (item.isNotBlank()) items.add(item)
                } else if (line.isNotBlank() && items.isNotEmpty() && !line.startsWith("#")) {
                    items.add(line)
                }
            }

            if (items.isEmpty() && lines.size > 1) {
                items.addAll(lines.drop(1))
            }

            return title to items
        }
    }
}
