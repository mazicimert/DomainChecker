package com.mehmetmertmazici.domainchecker.network


import android.util.Log
import com.mehmetmertmazici.domainchecker.model.ApiResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


 // API Service interface for domain operations

interface ApiService {
    @POST("tr/api/search")
    @FormUrlEncoded
    suspend fun searchDomains(@Field("domain") domain: String): ApiResponse

    @POST("tr/api/whois")
    @FormUrlEncoded
    suspend fun getWhoisInfo(@Field("domain") domain: String): String
}



// Custom response body converter to handle PHP warnings

class PhpWarningCleanupConverter private constructor(
    private val gson: Gson
) : Converter.Factory() {

    companion object {
        private const val TAG = "PhpWarningCleanup"

        // Regex patterns to match PHP warnings/errors
        private val PHP_WARNING_PATTERNS = listOf(
            Pattern.compile("<br\\s*/?>\\s*<b>Warning</b>:.*?<br\\s*/?>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
            Pattern.compile("<br\\s*/?>\\s*<b>Notice</b>:.*?<br\\s*/?>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
            Pattern.compile("<br\\s*/?>\\s*<b>Error</b>:.*?<br\\s*/?>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
            Pattern.compile("<br\\s*/?>\\s*<b>Fatal error</b>:.*?<br\\s*/?>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
            Pattern.compile("Warning:.*?\\n", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Notice:.*?\\n", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Error:.*?\\n", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Fatal error:.*?\\n", Pattern.CASE_INSENSITIVE)
        )

        fun create(): PhpWarningCleanupConverter {
            return PhpWarningCleanupConverter(
                GsonBuilder()
                    .setLenient()
                    .create()
            )
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val delegate = GsonConverterFactory.create(gson)
            .responseBodyConverter(type, annotations, retrofit)
            ?: return null

        return Converter<ResponseBody, Any> { responseBody ->
            val originalContent = responseBody.string()
            Log.d(TAG, "Original response: $originalContent")

            // Clean PHP warnings from response
            val cleanedContent = cleanPhpWarnings(originalContent)
            Log.d(TAG, "Cleaned response: $cleanedContent")

            // Check if response is just a plain text message (not JSON)
            if (!cleanedContent.trim().startsWith("{") && !cleanedContent.trim().startsWith("[")) {
                Log.d(TAG, "Response is plain text, creating error response")

                // Create a dummy error response for plain text responses
                val errorJson = """
                    {
                        "code": 0,
                        "status": "error",
                        "message": "$cleanedContent"
                    }
                """.trimIndent()

                val errorResponseBody = ResponseBody.create(
                    responseBody.contentType(),
                    errorJson
                )

                return@Converter delegate.convert(errorResponseBody)
            }

            // Create new ResponseBody with cleaned content
            val cleanedResponseBody = ResponseBody.create(
                responseBody.contentType(),
                cleanedContent
            )

            try {
                delegate.convert(cleanedResponseBody)
            } catch (e: Exception) {
                Log.e(TAG, "JSON parsing failed for cleaned content: $cleanedContent", e)

                // Try to extract JSON manually as fallback
                val jsonStart = cleanedContent.indexOf('{')
                val jsonEnd = cleanedContent.lastIndexOf('}')

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val jsonOnly = cleanedContent.substring(jsonStart, jsonEnd + 1)
                    Log.d(TAG, "Extracted JSON: $jsonOnly")

                    val jsonResponseBody = ResponseBody.create(
                        responseBody.contentType(),
                        jsonOnly
                    )

                    try {
                        delegate.convert(jsonResponseBody)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Manual JSON extraction also failed", e2)

                        // Last resort: create error response
                        val fallbackJson = """
                            {
                                "code": 0,
                                "status": "error",
                                "message": "Response parsing failed: $cleanedContent"
                            }
                        """.trimIndent()

                        val fallbackResponseBody = ResponseBody.create(
                            responseBody.contentType(),
                            fallbackJson
                        )

                        delegate.convert(fallbackResponseBody)
                    }
                } else {
                    // No JSON found, create error response
                    val errorJson = """
                        {
                            "code": 0,
                            "status": "error", 
                            "message": "$cleanedContent"
                        }
                    """.trimIndent()

                    val errorResponseBody = ResponseBody.create(
                        responseBody.contentType(),
                        errorJson
                    )

                    delegate.convert(errorResponseBody)
                }
            }
        }
    }

    private fun cleanPhpWarnings(content: String): String {
        var cleaned = content.trim()

        // Remove PHP warnings using regex patterns
        PHP_WARNING_PATTERNS.forEach { pattern ->
            cleaned = pattern.matcher(cleaned).replaceAll("")
        }

        // Remove HTML tags that might be left over
        cleaned = cleaned.replace(Regex("<[^>]*>"), "")

        // Remove any remaining line breaks before JSON
        cleaned = cleaned.replaceFirst(Regex("^[\\s\\r\\n]*"), "")

        return cleaned.trim()
    }
}

/**
 * Network error interceptor for better error handling
 */
class NetworkErrorInterceptor : Interceptor {
    companion object {
        private const val TAG = "NetworkError"
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        Log.d(TAG, "Making request to: ${request.url}")

        return try {
            val response = chain.proceed(request)
            Log.d(TAG, "Response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP error: ${response.code} ${response.message}")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "Network request failed", e)
            throw e
        }
    }
}

/**
 * API Client singleton for network operations
 */
object ApiClient {
    private const val BASE_URL = "https://devik.eurovdc.eu/"
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(NetworkErrorInterceptor())
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(ScalarsConverterFactory.create()) // Düz metin (whois) yanıtları için
        .addConverterFactory(PhpWarningCleanupConverter.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}