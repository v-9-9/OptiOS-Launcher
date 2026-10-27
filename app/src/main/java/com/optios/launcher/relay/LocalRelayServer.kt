package com.optios.launcher.relay

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OptiOS local-only LAN relay (no internet, opt-in).
 *
 * Security model:
 * - Server binds only when enabled by the user in OptiOS.
 * - Every request must carry the 6-digit code currently displayed on the
 *   launcher (rotates every 60 seconds).
 * - Serves the control page so the laptop browser can send notifications,
 *   toggle sleep, and see temperatures — nothing else.
 */
object RelayState {
    @Volatile var code: String = "------"
        private set
    @Volatile var port: Int = 8692
    @Volatile var enabled: Boolean = false
    @Volatile var server: LocalRelayServer? = null

    /** Fired on MainActivity's handler; shows overlay cards on screen. */
    @Volatile var overlayListener: ((RelayEvent) -> Unit)? = null

    data class RelayEvent(val title: String, val body: String)

    private val rnd = SecureRandom()

    fun rotateCode() {
        code = "%06d".format(Locale.US, rnd.nextInt(1_000_000))
    }

    fun validateCode(candidate: String?): Boolean =
        !candidate.isNullOrBlank() && candidate.trim() == code
}

class LocalRelayServer(
    private val port: Int,
    private val onLog: (String) -> Unit,
) : Thread("optios-relay") {

    private val running = AtomicBoolean(true)
    private var socket: ServerSocket? = null

    init {
        isDaemon = true
    }

    override fun run() {
        try {
            socket = ServerSocket(port)
            onLog("Relay listening on $port")
            while (running.get()) {
                val client = socket?.accept() ?: break
                handle(client)
            }
        } catch (_: Exception) {
            onLog("Relay stopped")
        }
    }

    fun shutdown() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun handle(client: java.net.Socket) {
        client.use { c ->
            val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val (method, rawPath) = parts[0] to parts[1]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).lowercase(Locale.US).trim()] = line.substring(i + 1).trim()
            }
            val body = if (headers["content-length"]?.toIntOrNull() ?: 0 > 0 && headers["content-length"]?.toIntOrNull()!! < 65536) {
                val buf = CharArray(headers["content-length"]!!.toInt())
                reader.read(buf, 0, buf.size)
                String(buf)
            } else ""
            val (path, query) = rawPath.split("?").let { it[0] to (it.getOrNull(1) ?: "") }
            val params = query.split("&").filter { it.contains("=") }
                .associate { it.substringBefore("=") to it.substringAfter("=") }

            val code = params["code"] ?: bodyFromKey(body, "code")
            when {
                method == "GET" && path == "/" ->
                    write(c, 200, controlPage())

                method == "GET" && path == "/api/status" ->
                    if (RelayState.validateCode(code)) write(c, 200, statusJson()) else denied(c)

                method == "POST" && path == "/api/notify" -> {
                    if (!RelayState.validateCode(code)) { denied(c); return@use }
                    val title = bodyFromKey(body, "title").ifBlank { "Notification" }
                    val bodyText = bodyFromKey(body, "body")
                    RelayState.overlayListener?.invoke(RelayState.RelayEvent(title, bodyText))
                    write(c, 200, """{"ok":true}""")
                }

                method == "POST" && path == "/api/sleep" -> {
                    if (!RelayState.validateCode(code)) { denied(c); return@use }
                    val state = bodyFromKey(body, "state")
                    SleepController.setSleep(state == "on")
                    write(c, 200, """{"ok":true,"sleep":$state}""")
                }

                else -> denied(c)
            }
        }
    }

    /** Minimal form-urlencoded extraction. */
    private fun bodyFromKey(body: String, key: String): String {
        return body.split("&").firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
    }

    private fun denied(c: java.net.Socket) = write(c, 403, """{"error":"invalid code"}""")

    private fun write(c: java.net.Socket, code: Int, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "DENIED"}\r\n" +
            "Content-Type: ${if (payload.startsWith("<") || payload.startsWith("<!")) "text/html; charset=utf-8" else "application/json"}\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        c.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun statusJson(): String {
        val t0 = readInt("/sys/class/thermal/thermal_zone0/temp")
        val t1 = readInt("/sys/class/thermal/thermal_zone1/temp")
        val mem = readMemAvailable()
        val now = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        return """{"ok":true,"time":"$now","cpu_temp":$t0,"gpu_temp":$t1,"mem_avail_kb":$mem,"sleep":${SleepController.isAsleep}}"""
    }

    private fun readInt(path: String): Int = try {
        val content = java.io.File(path).readText().trim()
        (content.toInt() / 1000)
    } catch (_: Exception) { 0 }

    private fun readMemAvailable(): Long = try {
        java.io.File("/proc/meminfo").readLines()
            .firstOrNull { it.startsWith("MemAvailable") }!!
            .substringAfter(":").trim().split(" ")[0].toLong()
    } catch (_: Exception) { 0 }

    /** Glass-dark control page served from the projector itself. */
    private fun controlPage(): String = """
<!doctype html><html><head><meta charset="utf-8"><meta name=viewport content="width=device-width,initial-scale=1">
<title>OptiOS — Local Control</title><style>
body{background:#0B0E13;color:#F2F4F8;font-family:system-ui,Segoe UI,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}
.card{background:linear-gradient(180deg,#33FFFFFF,#14FFFFFF);border:1px solid #26FFFFFF;border-radius:24px;padding:28px;width:min(420px,92vw)}
h1{margin:0 0 4px;font-weight:300;letter-spacing:1px}small{opacity:.6}
input,button{width:100%;padding:12px;margin-top:10px;border-radius:12px;border:1px solid #26FFFFFF;background:#0E1116;color:#F2F4F8;box-sizing:border-box}
button{background:#63B3F7;color:#0B0E13;font-weight:700;cursor:pointer;border:none}
.row{display:flex;gap:8px}.row>*{flex:1}
#out{margin-top:14px;font-size:13px;opacity:.8;min-height:40px;white-space:pre-wrap}
</style></head><body><div class=card>
<h1>OptiOS</h1><small>Local-only control — code rotates every 60s on the projector</small>
<input id=code inputmode=numeric placeholder="6-digit code (on projector screen)">
<input id=title placeholder="Title e.g. YouTube">
<input id=body placeholder="Message body">
<button onclick=send()>Send notification</button>
<div class=row>
<button onclick=sleepOn()>Sleep</button><button onclick=sleepOff()>Wake</button>
</div>
<button onclick=status()>Status (temps / RAM)</button>
<div id=out></div><script>
async function post(path){const code=document.getElementById('code').value.trim();
const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'code='+encodeURIComponent(code)+'&'+getPathArgs(path)});
document.getElementById('out').textContent=JSON.stringify(await r.json())}
function getPathArgs(path){
const title=document.getElementById('title').value,body=document.getElementById('body').value;
return ('title='+encodeURIComponent(title)+'&body='+encodeURIComponent(body)+'&state='+(path.includes('sleep')?(document.getElementById('out').dataset.sleep==='off'?'on':'off'):''));}
function send(){document.getElementById('out').dataset.sleep='off';post('/api/notify')}
function sleepOn(){document.getElementById('out').dataset.sleep='on';post('/api/sleep')}
function sleepOff(){document.getElementById('out').dataset.sleep='off';post('/api/sleep')}
async function status(){const code=document.getElementById('code').value.trim();
const r=await fetch('/api/status?code='+encodeURIComponent(code));
document.getElementById('out').textContent=JSON.stringify(await r.json())}
</script></div></body></html>"""
}

/** Sleep = pure OptiOS overlay; no root, no system changes. */
object SleepController {
    @Volatile var isAsleep: Boolean = false
    @Volatile var sleepListener: ((Boolean) -> Unit)? = null

    fun setSleep(asleep: Boolean) {
        isAsleep = asleep
        sleepListener?.invoke(asleep)
    }
}
