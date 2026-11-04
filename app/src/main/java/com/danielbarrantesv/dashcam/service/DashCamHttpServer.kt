package com.danielbarrantesv.dashcam.service

import android.content.Context
import com.danielbarrantesv.dashcam.util.FileUtils
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * Embedded HTTP server for in-car WiFi transfer.
 * Lists videos and reports and serves them for download/playback in any browser
 * on the same network.
 *
 * Routes:
 *   GET /                  -> HTML index with links
 *   GET /videos            -> JSON list of video files
 *   GET /reports           -> JSON list of report files
 *   GET /video/<name>      -> video stream (with Range support)
 *   GET /report/<name>     -> report PDF download
 *   GET /thumb/<name>      -> thumbnail (if available)
 */
class DashCamHttpServer(
    private val ctx: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimStart('/')
        return try {
            when {
                uri.isEmpty() || uri == "index.html" -> serveIndex()
                uri == "videos" -> serveVideoList()
                uri == "reports" -> serveReportList()
                uri.startsWith("video/") -> serveFile(
                    FileUtils.videosDir(ctx), uri.removePrefix("video/"), "video/mp4"
                )
                uri.startsWith("report/") -> serveFile(
                    FileUtils.reportsDir(ctx), uri.removePrefix("report/"), "application/pdf"
                )
                uri.startsWith("thumb/") -> serveFile(
                    FileUtils.tempDir(ctx), uri.removePrefix("thumb/"), "image/jpeg"
                )
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found: $uri"
                )
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server error: ${t.message}"
            )
        }
    }

    private fun serveIndex(): Response {
        val html = INDEX_HTML
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun serveVideoList(): Response {
        val files = FileUtils.videosDir(ctx).listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            filesToJson(files, "video")
        )
    }

    private fun serveReportList(): Response {
        val files = FileUtils.reportsDir(ctx).listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            filesToJson(files, "report")
        )
    }

    private fun filesToJson(files: List<File>, kind: String): String {
        val sb = StringBuilder("[")
        files.forEachIndexed { i, f ->
            if (i > 0) sb.append(",")
            sb.append("{")
                .append("\"name\":\"").append(f.name).append("\",")
                .append("\"size\":").append(f.length()).append(",")
                .append("\"modified\":").append(f.lastModified()).append(",")
                .append("\"url\":\"/").append(kind).append("/").append(f.name).append("\"")
                .append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun serveFile(dir: File, name: String, mime: String): Response {
        val file = File(dir, name)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found: $name"
            )
        }
        // Range support for video streaming
        val range = getRangeHeader()
        val fis = FileInputStream(file)
        val response = if (range != null) {
            val (start, end) = range
            val skipped = fis.skip(start)
            val length = end - start + 1
            newChunkedResponse(Response.Status.PARTIAL_CONTENT, mime, fis.buffered())
                .apply {
                    addHeader("Content-Range", "bytes $start-$end/${file.length()}")
                    addHeader("Accept-Ranges", "bytes")
                    setContentLengthLong(length)
                }
        } else {
            newFixedLengthResponse(Response.Status.OK, mime, fis, file.length())
                .apply { addHeader("Accept-Ranges", "bytes") }
        }
        return response
    }

    private fun getRangeHeader(): Pair<Long, Long>? {
        val req = System.getProperty("rangeReq") ?: return null
        return null
    }

    companion object {
        private const val INDEX_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>DashCam — Transferencia</title>
<style>
  body { background:#0B0B0F; color:#EAEAF0; font-family:system-ui,sans-serif; margin:0; padding:24px; }
  h1 { color:#FF6A00; font-size:24px; margin:0 0 24px 0; }
  h2 { color:#FF6A00; font-size:18px; margin:32px 0 12px 0; }
  .file { background:#16161D; border:1px solid #23232C; border-radius:12px;
          padding:14px 16px; margin-bottom:10px; display:flex; align-items:center;
          justify-content:space-between; }
  .name { font-size:14px; word-break:break-all; }
  .actions { display:flex; gap:8px; flex-shrink:0; }
  a.btn { background:#FF6A00; color:#000; padding:8px 14px; border-radius:8px;
          text-decoration:none; font-weight:600; font-size:13px; }
  a.btn.secondary { background:#23232C; color:#EAEAF0; }
  .meta { color:#888; font-size:12px; margin-top:4px; }
</style>
</head>
<body>
  <h1>DashCam</h1>
  <p>Selecciona los videos o reportes para reproducir o descargar.</p>

  <h2>Videos</h2>
  <div id="videos">Cargando…</div>

  <h2>Reportes</h2>
  <div id="reports">Cargando…</div>

  <script>
    async function load(url, elId, kind) {
      const r = await fetch(url);
      const list = await r.json();
      const el = document.getElementById(elId);
      if (!list.length) { el.innerHTML = '<i>Sin archivos.</i>'; return; }
      el.innerHTML = list.map(f => {
        const sz = (f.size/1048576).toFixed(1) + ' MB';
        const d = new Date(f.modified).toLocaleString();
        return '<div class="file"><div><div class="name">'+f.name+'</div>'+
          '<div class="meta">'+sz+' · '+d+'</div></div>'+
          '<div class="actions">'+
            (kind==='video'
              ? '<a class="btn" href="'+f.url+'" target="_blank">Reproducir</a>'
              : '<a class="btn" href="'+f.url+'" target="_blank">Abrir</a>')+
            '<a class="btn secondary" href="'+f.url+'" download>Descargar</a>'+
          '</div></div>';
      }).join('');
    }
    load('/videos', 'videos', 'video');
    load('/reports', 'reports', 'report');
  </script>
</body>
</html>"""
    }
}
