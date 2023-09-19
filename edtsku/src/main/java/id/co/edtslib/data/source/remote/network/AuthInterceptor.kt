package id.co.edtslib.data.source.remote.network

import id.co.edtslib.EdtsKu
import id.co.edtslib.data.source.local.HttpHeaderLocalSource
import id.co.edtslib.util.SecurityUtil
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.security.PrivateKey

/**
 * A {@see RequestInterceptor} that adds an auth token to requests
 *
 * @constructor Create an auth interceptor
 * */
class AuthInterceptor(
    private val httpHeaderLocalSource: HttpHeaderLocalSource,
    private val apps: String
) : Interceptor {

    private var privateKey: PrivateKey? = null

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val headers = httpHeaderLocalSource.getCached()
        val builder = chain.request().newBuilder()
        if (headers != null) {
            for ((k, v) in headers) {
                if (v != null) {
                    builder.addHeader(k, v)
                }
            }
        }
        builder.addHeader("apps", apps)
        if (EdtsKu.privateKeyFileContent != null && EdtsKu.defaultPayload != null &&
            EdtsKu.enableSignature) {
            privateKey = SecurityUtil.getPrivateKeyFromKeyStore(EdtsKu.privateKeyFileContent!!.split(", "))
            val requestCopy = builder.build()
            val signature = getSignature(requestCopy, EdtsKu.defaultPayload!!)
            builder.addHeader("signature", signature)
        }

        builder.removeHeader("pathSignature")
        builder.removeHeader("usingAppsSignature")

        return chain.proceed(builder.build())
    }

    private fun getSignature(requestCopy: Request, defaultPayload: String): String {
        val method = requestCopy.method
        if (method == "GET") {
            return SecurityUtil.signWithPayload(defaultPayload, privateKey)
        }

        val contentType = requestCopy.body?.contentType()?.toString()
        val usingAppsSignature = requestCopy.header("usingAppsSignature")
        if (contentType?.contains("multipart/form-data") == true ||
            usingAppsSignature == "true") {
            return SecurityUtil.signWithPayload(apps, privateKey)
        }

        val body = minifyRequestBody(requestCopy.body)
        if (body.isNotEmpty()) {
            return SecurityUtil.signWithPayload(body, privateKey)
        }

        val query = requestCopy.url.encodedQuery
        val queryValue = extractQuery(query)
        if (queryValue.isNotEmpty()) {
            return SecurityUtil.signWithPayload(queryValue, privateKey)
        }

        val pathParamReverseIdx = requestCopy.header("pathSignature")
        if (pathParamReverseIdx != null) {
            return try {
                val pathIdx = pathParamReverseIdx.toInt()
                val path = requestCopy.url.encodedPath
                val pathParam = extractPathParam(path, pathIdx)
                SecurityUtil.signWithPayload(pathParam, privateKey)
            } catch (_: Exception) {
                SecurityUtil.signWithPayload(defaultPayload, privateKey)
            }
        }

        return SecurityUtil.signWithPayload(defaultPayload, privateKey)
    }

    private fun minifyRequestBody(body: RequestBody?): String {
        return try {
            val buffer = Buffer()
            if (body == null) {
                return ""
            }
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            ""
        }
    }

    private fun extractQuery(query: String?): String {
        if (query != null) {
            val excluded = mutableListOf("page", "unpaged", "sort", "size")
            val queries = query.split("&")
            if (queries.isNotEmpty()) {
                for (q in queries) {
                    val key = q.split("=")[0]
                    if (!excluded.contains(key)) {
                        return q.split("=")[1]
                    }
                }
            }
        }
        return ""
    }

    private fun extractPathParam(path: String, idx: Int): String {
        val pathSegments = path.split("/")
        val size = pathSegments.size

        val pathIndex = size-1-idx
        return if (pathIndex < size) {
            pathSegments[pathIndex]
        } else {
            ""
        }
    }

}