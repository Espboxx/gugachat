package com.gugachat.app.data.sync

import com.gugachat.app.data.api.awaitCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavCredentials(
    val username: String,
    val password: String,
) {
    fun basicAuthHeader(): String = Credentials.basic(username, password, StandardCharsets.UTF_8)

    override fun toString(): String = "WebDavCredentials(username=$username, password=***)"
}

data class WebDavResource(
    val href: String,
    val normalizedPath: String,
    val displayName: String,
    val isCollection: Boolean,
    val contentLength: Long = -1L,
    val lastModified: String? = null,
    val etag: String? = null,
)

class WebDavException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Pure OkHttp WebDAV client tailored for Jianguoyun, Nextcloud, and Alist/OpenList:
 * - Strictly enforces `Depth: 0` or `Depth: 1` (never `Depth: infinity`).
 * - Forces trailing `/` on directory operations and treats both `201` and `405` on `MKCOL` as success.
 * - Uses preemptive Basic auth and `file.asRequestBody(...)` for explicit `Content-Length` on `PUT`.
 * - Bounds concurrent network calls (`<= 2`) and retries `429` / `503` with exponential backoff.
 * - Parses `207 Multi-Status` XML via `XmlPullParserFactory` with pure-JVM DOM fallback.
 */
class WebDavClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val retryBaseDelayMs: Long = DEFAULT_RETRY_BASE_DELAY_MS,
) {
    init {
        require(maxConcurrency in 1..2) { "WebDAV maxConcurrency must be 1 or 2 (got $maxConcurrency)" }
    }

    private val semaphore = Semaphore(maxConcurrency)

    suspend fun propfind(
        url: String,
        depth: Int,
        creds: WebDavCredentials,
    ): List<WebDavResource> {
        require(depth == 0 || depth == 1) {
            "WebDAV PROPFIND depth must be strictly 0 or 1 (got $depth); Depth: infinity is forbidden"
        }
        val targetUrl = if (depth == 1) normalizeDirectoryUrl(url) else url.trim()
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .method("PROPFIND", PROPFIND_REQUEST_BODY)
            .header("Authorization", creds.basicAuthHeader())
            .header("Depth", depth.toString())
            .header("Accept", "application/xml, text/xml, */*")
            .header("User-Agent", USER_AGENT)

        return executeWithRetry { requestBuilder.build() }.use { resp ->
            val rawXml = withContext(Dispatchers.IO) { resp.body?.string().orEmpty() }
            if (resp.code != 207 && !resp.isSuccessful) {
                throw WebDavException(resp.code, "PROPFIND $targetUrl failed with HTTP ${resp.code}")
            }
            if (rawXml.isBlank()) {
                emptyList()
            } else {
                parseMultiStatusXml(rawXml, targetUrl)
            }
        }
    }

    suspend fun listDirectoryIfExists(
        dirUrl: String,
        creds: WebDavCredentials,
    ): List<WebDavResource> {
        val normalizedDirUrl = normalizeDirectoryUrl(dirUrl)
        val allEntries = try {
            propfind(normalizedDirUrl, depth = 1, creds = creds)
        } catch (e: WebDavException) {
            if (e.statusCode == 404) return emptyList()
            throw e
        }
        val selfPath = extractNormalizedPath(normalizedDirUrl)
        return allEntries.filterNot { res ->
            res.normalizedPath == selfPath
        }
    }

    suspend fun exists(
        url: String,
        creds: WebDavCredentials,
    ): Boolean {
        return try {
            propfind(url, depth = 0, creds = creds)
            true
        } catch (e: WebDavException) {
            if (e.statusCode == 404) false else throw e
        }
    }

    suspend fun ensureDirectoryHierarchy(
        rootUrl: String,
        subPath: String,
        creds: WebDavCredentials,
    ): String {
        val cleanRoot = normalizeDirectoryUrl(rootUrl)
        val segments = subPath
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (seg in segments) {
            require(seg != "." && seg != "..") { "Invalid WebDAV directory segment: '$seg'" }
        }

        if (segments.isEmpty()) {
            ensureSingleDirectory(cleanRoot, creds)
            return cleanRoot
        }

        var currentUrl = cleanRoot
        for (seg in segments) {
            currentUrl = normalizeDirectoryUrl(resolveUrl(currentUrl, seg))
            ensureSingleDirectory(currentUrl, creds)
        }
        return currentUrl
    }

    private suspend fun ensureSingleDirectory(
        dirUrl: String,
        creds: WebDavCredentials,
    ) {
        val target = normalizeDirectoryUrl(dirUrl)
        try {
            val resources = propfind(target, depth = 0, creds = creds)
            val self = resources.firstOrNull()
            if (self != null && !self.isCollection) {
                throw WebDavException(409, "Remote path exists and is not a directory: $target")
            }
            return
        } catch (e: WebDavException) {
            if (e.statusCode != 404) throw e
        }

        executeWithRetry {
            Request.Builder()
                .url(target)
                .method("MKCOL", null)
                .header("Authorization", creds.basicAuthHeader())
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { resp ->
            val code = resp.code
            if (code == 201 || code == 200 || code == 204 || code == 405) {
                return
            }
            throw WebDavException(code, "MKCOL $target failed with HTTP $code")
        }
    }

    suspend fun putFileFixedLength(
        url: String,
        file: File,
        contentType: String,
        creds: WebDavCredentials,
    ) {
        require(file.isFile) { "Upload source file does not exist: ${file.path}" }
        val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val fileLen = file.length()

        executeWithRetry {
            val body = file.asRequestBody(mediaType)
            Request.Builder()
                .url(url.trim())
                .put(body)
                .header("Authorization", creds.basicAuthHeader())
                .header("Content-Length", fileLen.toString())
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { resp ->
            if (!resp.isSuccessful) {
                throw WebDavException(resp.code, "PUT $url failed with HTTP ${resp.code}")
            }
        }
    }

    suspend fun putBytesFixedLength(
        url: String,
        bytes: ByteArray,
        contentType: String,
        creds: WebDavCredentials,
    ) {
        val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        executeWithRetry {
            val body = bytes.toRequestBody(mediaType)
            Request.Builder()
                .url(url.trim())
                .put(body)
                .header("Authorization", creds.basicAuthHeader())
                .header("Content-Length", bytes.size.toString())
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { resp ->
            if (!resp.isSuccessful) {
                throw WebDavException(resp.code, "PUT $url failed with HTTP ${resp.code}")
            }
        }
    }

    suspend fun getToFile(
        url: String,
        destFile: File,
        creds: WebDavCredentials,
    ) {
        destFile.parentFile?.mkdirs()
        val tmp = File(destFile.parentFile, "${destFile.name}.${System.nanoTime()}.part")
        var succeeded = false
        try {
            executeWithRetry {
                Request.Builder()
                    .url(url.trim())
                    .get()
                    .header("Authorization", creds.basicAuthHeader())
                    .header("User-Agent", USER_AGENT)
                    .build()
            }.use { resp ->
                if (!resp.isSuccessful) {
                    throw WebDavException(resp.code, "GET $url failed with HTTP ${resp.code}")
                }
                val body = resp.body ?: throw WebDavException(resp.code, "Empty WebDAV GET body for $url")
                withContext(Dispatchers.IO) {
                    val buf = ByteArray(IO_BUFFER_BYTES)
                    BufferedInputStream(body.byteStream(), IO_BUFFER_BYTES).use { ins ->
                        BufferedOutputStream(FileOutputStream(tmp), IO_BUFFER_BYTES).use { outs ->
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                outs.write(buf, 0, n)
                            }
                            outs.flush()
                        }
                    }
                }
            }
            if (destFile.exists() && !destFile.delete()) {
                throw IOException("Cannot overwrite destination file: ${destFile.path}")
            }
            if (!tmp.renameTo(destFile)) {
                tmp.copyTo(destFile, overwrite = true)
            }
            succeeded = true
        } finally {
            if (!succeeded || tmp.exists()) {
                tmp.delete()
            }
        }
    }

    suspend fun getBytes(
        url: String,
        creds: WebDavCredentials,
        maxBytes: Long = 16L * 1024L * 1024L,
    ): ByteArray {
        return executeWithRetry {
            Request.Builder()
                .url(url.trim())
                .get()
                .header("Authorization", creds.basicAuthHeader())
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { resp ->
            if (!resp.isSuccessful) {
                throw WebDavException(resp.code, "GET $url failed with HTTP ${resp.code}")
            }
            val body = resp.body ?: return@use ByteArray(0)
            withContext(Dispatchers.IO) {
                val len = body.contentLength()
                if (len > maxBytes) {
                    throw WebDavException(413, "WebDAV response exceeds max bytes ($len > $maxBytes)")
                }
                body.bytes()
            }
        }
    }

    suspend fun delete(
        url: String,
        creds: WebDavCredentials,
        ignoreNotFound: Boolean = false,
    ): Boolean {
        return executeWithRetry {
            Request.Builder()
                .url(url.trim())
                .delete()
                .header("Authorization", creds.basicAuthHeader())
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { resp ->
            when {
                resp.isSuccessful -> true
                ignoreNotFound && resp.code == 404 -> false
                else -> throw WebDavException(resp.code, "DELETE $url failed with HTTP ${resp.code}")
            }
        }
    }

    private suspend fun executeWithRetry(requestFactory: () -> Request): okhttp3.Response {
        var attempt = 0
        while (true) {
            val response = try {
                semaphore.withPermit {
                    httpClient.awaitCall(requestFactory())
                }
            } catch (e: IOException) {
                if (attempt < maxRetries) {
                    attempt += 1
                    delay(computeBackoffDelayMs(attempt, retryAfterHeader = null))
                    continue
                }
                throw e
            }

            if ((response.code == 429 || response.code == 503) && attempt < maxRetries) {
                val retryAfter = response.header("Retry-After")
                response.close()
                attempt += 1
                delay(computeBackoffDelayMs(attempt, retryAfterHeader = retryAfter))
                continue
            }
            return response
        }
    }

    private fun computeBackoffDelayMs(attempt: Int, retryAfterHeader: String?): Long {
        val parsedSec = retryAfterHeader?.trim()?.toLongOrNull()?.coerceIn(0L, 10L)
        if (parsedSec != null && retryBaseDelayMs > 0L) {
            return (parsedSec * 1000L).coerceAtLeast(retryBaseDelayMs)
        }
        val shift = (attempt - 1).coerceIn(0, 5)
        return retryBaseDelayMs * (1L shl shift)
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 2
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_RETRY_BASE_DELAY_MS = 400L
        private const val IO_BUFFER_BYTES = 64 * 1024
        private const val USER_AGENT = "GuGuChat-Android/0.4 WebDAV"

        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val PROPFIND_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:resourcetype/>
                <D:getcontentlength/>
                <D:getlastmodified/>
                <D:getetag/>
                <D:displayname/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
        private val PROPFIND_REQUEST_BODY = PROPFIND_XML.toRequestBody(XML_MEDIA_TYPE)

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun normalizeDirectoryUrl(rawUrl: String): String {
            val clean = rawUrl.trim()
            require(clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
                "WebDAV URL must start with http:// or https://"
            }
            val parsed = clean.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid WebDAV URL: $rawUrl")
            val segs = parsed.pathSegments.filter { it.isNotEmpty() }
            val builder = parsed.newBuilder().encodedPath("/")
            for (seg in segs) {
                builder.addPathSegment(seg)
            }
            val joined = builder.build().toString()
            return if (joined.endsWith("/")) joined else "$joined/"
        }

        fun resolveUrl(baseUrl: String, relativePath: String): String {
            val dirUrl = normalizeDirectoryUrl(baseUrl)
            val parsed = dirUrl.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid WebDAV base URL: $baseUrl")
            val cleanRel = relativePath.replace('\\', '/').trimStart('/')
            if (cleanRel.isEmpty()) return dirUrl
            val isDir = cleanRel.endsWith("/")
            val segments = cleanRel.split('/').filter { it.isNotEmpty() }
            val builder = parsed.newBuilder()
            for (seg in segments) {
                require(seg != "." && seg != "..") { "Invalid relative WebDAV segment: '$seg'" }
                builder.addPathSegment(seg)
            }
            val built = builder.build().toString()
            return if (isDir && !built.endsWith("/")) "$built/" else built
        }

        internal fun extractNormalizedPath(hrefOrUrl: String): String {
            val trimmed = hrefOrUrl.trim()
            if (trimmed.isEmpty()) return "/"
            val rawPath = runCatching {
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    URI(trimmed).rawPath
                } else {
                    URI("http://localhost/${trimmed.trimStart('/')}").rawPath
                }
            }.getOrNull() ?: trimmed.substringBefore('?').substringBefore('#')

            val decoded = runCatching {
                URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            }.getOrDefault(rawPath)

            val collapsed = decoded.replace(Regex("/+"), "/").trimEnd('/')
            return if (collapsed.isEmpty()) "/" else collapsed
        }

        internal fun parseMultiStatusXml(
            xml: String,
            @Suppress("UNUSED_PARAMETER") requestUrl: String = "",
        ): List<WebDavResource> {
            val trimmed = xml.removePrefix("\uFEFF").trim()
            if (trimmed.isEmpty()) return emptyList()

            tryPullParser(trimmed)?.let { return it }
            return parseWithSecureDom(trimmed)
        }

        private fun tryPullParser(xml: String): List<WebDavResource>? {
            val parser = runCatching {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                factory.newPullParser()
            }.getOrNull() ?: return null

            // Guard against Android mockable jar returning 0-returning no-op stubs in pure-JVM tests
            return runCatching {
                parser.setInput(StringReader(xml))
                val initialEvent = parser.eventType
                if (initialEvent != XmlPullParser.START_DOCUMENT) return null
                val firstAdvanced = parser.next()
                if (firstAdvanced == XmlPullParser.START_DOCUMENT) return null
                parseFromPullParser(parser, firstAdvanced)
            }.getOrNull()
        }

        private fun parseFromPullParser(
            parser: XmlPullParser,
            initialEvent: Int = parser.eventType,
        ): List<WebDavResource> {
            val resources = ArrayList<WebDavResource>()

            var inResponse = false
            var inPropstat = false
            var inProp = false
            var inResourceType = false

            var href = ""
            var displayName = ""
            var isCollection = false
            var contentLength = -1L
            var lastModified: String? = null
            var etag: String? = null

            var propstatStatus = ""
            var psDisplayName: String? = null
            var psIsCollection = false
            var psContentLength: Long? = null
            var psLastModified: String? = null
            var psEtag: String? = null

            var eventType = initialEvent
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val local = localTagName(parser.name)
                        when {
                            local.equals("response", ignoreCase = true) -> {
                                inResponse = true
                                href = ""
                                displayName = ""
                                isCollection = false
                                contentLength = -1L
                                lastModified = null
                                etag = null
                            }
                            inResponse && local.equals("href", ignoreCase = true) -> {
                                href = parser.nextText().trim()
                            }
                            inResponse && local.equals("propstat", ignoreCase = true) -> {
                                inPropstat = true
                                propstatStatus = ""
                                psDisplayName = null
                                psIsCollection = false
                                psContentLength = null
                                psLastModified = null
                                psEtag = null
                            }
                            inPropstat && local.equals("status", ignoreCase = true) -> {
                                propstatStatus = parser.nextText().trim()
                            }
                            inPropstat && local.equals("prop", ignoreCase = true) -> {
                                inProp = true
                            }
                            inProp && local.equals("resourcetype", ignoreCase = true) -> {
                                inResourceType = true
                            }
                            inResourceType && local.equals("collection", ignoreCase = true) -> {
                                psIsCollection = true
                            }
                            inProp && local.equals("displayname", ignoreCase = true) -> {
                                psDisplayName = parser.nextText().trim().takeIf { it.isNotEmpty() }
                            }
                            inProp && local.equals("getcontentlength", ignoreCase = true) -> {
                                psContentLength = parser.nextText().trim().toLongOrNull()
                            }
                            inProp && local.equals("getlastmodified", ignoreCase = true) -> {
                                psLastModified = parser.nextText().trim().takeIf { it.isNotEmpty() }
                            }
                            inProp && local.equals("getetag", ignoreCase = true) -> {
                                psEtag = parser.nextText().trim().takeIf { it.isNotEmpty() }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val local = localTagName(parser.name)
                        when {
                            local.equals("resourcetype", ignoreCase = true) -> {
                                inResourceType = false
                            }
                            local.equals("prop", ignoreCase = true) -> {
                                inProp = false
                            }
                            local.equals("propstat", ignoreCase = true) -> {
                                val ok = propstatStatus.isEmpty() || propstatStatus.contains(" 200 ") || propstatStatus.contains(" 20")
                                if (ok) {
                                    if (psIsCollection) isCollection = true
                                    if (psDisplayName != null) displayName = psDisplayName!!
                                    if (psContentLength != null) contentLength = psContentLength!!
                                    if (psLastModified != null) lastModified = psLastModified
                                    if (psEtag != null) etag = psEtag
                                }
                                inPropstat = false
                            }
                            local.equals("response", ignoreCase = true) -> {
                                if (inResponse && href.isNotEmpty()) {
                                    val normPath = extractNormalizedPath(href)
                                    val fallbackName = normPath.substringAfterLast('/').ifEmpty { "/" }
                                    resources.add(
                                        WebDavResource(
                                            href = href,
                                            normalizedPath = normPath,
                                            displayName = displayName.ifEmpty { fallbackName },
                                            isCollection = isCollection || href.trimEnd().endsWith("/"),
                                            contentLength = contentLength,
                                            lastModified = lastModified,
                                            etag = etag,
                                        ),
                                    )
                                }
                                inResponse = false
                            }
                        }
                    }
                }
                val nextEvent = parser.next()
                if (nextEvent == XmlPullParser.START_DOCUMENT) {
                    throw IllegalStateException("Unexpected START_DOCUMENT from pull parser")
                }
                eventType = nextEvent
            }
            return resources
        }

        private fun parseWithSecureDom(xml: String): List<WebDavResource> {
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val doc = dbf.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(xml)))
            val out = ArrayList<WebDavResource>()
            val responseNodes = doc.documentElement?.let { root ->
                collectElementsByLocalName(root, "response")
            }.orEmpty()

            for (respEl in responseNodes) {
                val href = firstDirectChildText(respEl, "href")?.trim().orEmpty()
                if (href.isEmpty()) continue

                var isCollection = href.trimEnd().endsWith("/")
                var displayName = ""
                var contentLength = -1L
                var lastModified: String? = null
                var etag: String? = null

                val propstats = collectDirectChildren(respEl, "propstat")
                for (ps in propstats) {
                    val status = firstDirectChildText(ps, "status").orEmpty()
                    if (status.isNotEmpty() && !status.contains(" 20")) continue
                    val prop = collectDirectChildren(ps, "prop").firstOrNull() ?: continue
                    val rt = collectDirectChildren(prop, "resourcetype").firstOrNull()
                    if (rt != null && collectDirectChildren(rt, "collection").isNotEmpty()) {
                        isCollection = true
                    }
                    firstDirectChildText(prop, "displayname")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        displayName = it
                    }
                    firstDirectChildText(prop, "getcontentlength")?.trim()?.toLongOrNull()?.let {
                        contentLength = it
                    }
                    firstDirectChildText(prop, "getlastmodified")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        lastModified = it
                    }
                    firstDirectChildText(prop, "getetag")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        etag = it
                    }
                }

                val normPath = extractNormalizedPath(href)
                val fallbackName = normPath.substringAfterLast('/').ifEmpty { "/" }
                out.add(
                    WebDavResource(
                        href = href,
                        normalizedPath = normPath,
                        displayName = displayName.ifEmpty { fallbackName },
                        isCollection = isCollection,
                        contentLength = contentLength,
                        lastModified = lastModified,
                        etag = etag,
                    ),
                )
            }
            return out
        }

        private fun collectElementsByLocalName(root: Element, targetLocal: String): List<Element> {
            val out = ArrayList<Element>()
            val children = root.childNodes
            for (i in 0 until children.length) {
                val n = children.item(i)
                if (n.nodeType == Node.ELEMENT_NODE) {
                    val el = n as Element
                    if (localTagName(el.localName ?: el.tagName).equals(targetLocal, ignoreCase = true)) {
                        out.add(el)
                    }
                }
            }
            return out
        }

        private fun collectDirectChildren(parent: Element, targetLocal: String): List<Element> =
            collectElementsByLocalName(parent, targetLocal)

        private fun firstDirectChildText(parent: Element, targetLocal: String): String? {
            val child = collectDirectChildren(parent, targetLocal).firstOrNull() ?: return null
            return child.textContent
        }

        private fun localTagName(rawName: String?): String {
            if (rawName.isNullOrEmpty()) return ""
            val idx = rawName.indexOf(':')
            return if (idx >= 0) rawName.substring(idx + 1) else rawName
        }
    }
}
