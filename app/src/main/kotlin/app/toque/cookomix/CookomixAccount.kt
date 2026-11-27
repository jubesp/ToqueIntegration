package app.toque.cookomix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import app.toque.ToqueApp
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.obj
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Recettes de cookomix.com compatibles avec le Monsieur Cuisine Connect (portage de
 * aguiran/MrCuisineConnectCookomix sur Toque 0.11).
 *
 * Cookomix n'a pas d'API ouverte : la liste vient des sitemaps du site (avec la date de mise à jour
 * de chaque recette), puis seules les pages nouvelles ou modifiées sont téléchargées et converties
 * ([Cookomix.parsePage]). Les recettes incompatibles sont mémorisées pour ne pas être retéléchargées
 * tant qu'elles ne changent pas. Requêtes espacées (plus d'une seconde) ; reprend où elle s'est arrêtée.
 *
 * Panne ou changement du site : la synchro s'arrête avec un code d'erreur ([Cookomix.Error],
 * docs/COOKOMIX-ERREURS.md) et ne supprime alors aucune recette. Une page illisible n'efface jamais
 * la version déjà enregistrée : elle sera relue à la synchro suivante.
 *
 * Tout passe par un seul fil d'arrière-plan ; les changements sont annoncés sur le fil de l'interface.
 */
class CookomixAccount(private val app: ToqueApp) {
    private val prefs get() = app.prefs
    private val repo get() = app.recipes
    private val log get() = app.log
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "toque-cookomix").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val listeners = ArrayList<() -> Unit>()
    private val indexFile = File(app.filesDir, "cookomix-index.json")
    @Volatile private var stop = false
    @Volatile private var lastNotify = 0L

    class Result(val ok: Boolean, val message: String, val count: Int = 0)

    /** Synchro en cours. */
    val busy: Boolean get() = running.get()

    /** Dernier message d'avancement de la synchro en cours (vide sinon). */
    @Volatile var progress: String = ""
        private set

    /** Prévient (sur le fil de l'interface) quand la synchro avance ou se termine. */
    fun onChange(l: () -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    private fun changed() = main.post { for (l in ArrayList(listeners)) l() }

    private fun report(msg: String) {
        progress = msg
        val now = System.currentTimeMillis()
        if (now - lastNotify >= 700) {
            lastNotify = now
            changed()
        }
    }

    /** Arrêt demandé : la synchro s'interrompt après la page en cours, ce qui est déjà fait reste. */
    fun requestStop() {
        if (busy) stop = true
    }

    /** Active Cookomix et lance la première synchro. */
    fun enable(done: (Result?) -> Unit = {}) {
        prefs.cookomixEnabled = true
        changed()
        sync(done)
    }

    /** Désactive : la synchro s'arrête, les recettes Cookomix et leurs photos partent du robot. */
    fun disable() {
        stop = true
        prefs.forgetCookomix()
        log.write("cookomix", "recettes Cookomix désactivées")
        changed()
        exec.execute {
            writeIndex(emptyMap())
            try {
                val photos = repo.index.entries.filter { Cookomix.isCookomix(it.id) }.map { it.image }
                repo.removeCookomixExcept(emptySet())
                for (url in photos) {
                    val name = photoName(url) ?: continue
                    if (File(repo.imagesDir, name).delete()) repo.photoRemoved(name)
                }
            } catch (e: Exception) {
                log.crash("cookomix", e)
            }
            changed()
        }
    }

    /** Synchro en arrière-plan ; [done] reçoit le résultat sur le fil de l'interface (null : rien à faire). */
    fun sync(done: (Result?) -> Unit = {}) {
        exec.execute {
            val r = syncNow()
            main.post { done(r) }
        }
    }

    /** Synchro (fil d'arrière-plan, bloquante) : la nuit (si demandé) et par le bouton des Réglages. */
    fun syncNow(): Result? {
        if (!prefs.cookomixEnabled) return null
        if (!running.compareAndSet(false, true)) return null
        stop = false
        changed()
        val r = try {
            run()
        } catch (e: Exception) {
            log.crash("cookomix", e)
            failure(Cookomix.Error.INTERNAL, e.message ?: e.javaClass.simpleName)
        } finally {
            progress = ""
            running.set(false)
        }
        if (prefs.cookomixEnabled) {
            prefs.cookomixLastMs = System.currentTimeMillis()
            prefs.cookomixLastResult = SimpleDateFormat(tr("dd/MM à HH:mm"), I18n.locale).format(Date()) + " : " + r.message
        }
        log.write("cookomix", r.message)
        changed()
        return r
    }

    // ------------------------------------------------------------------ synchro

    /** Ligne d'index : date du sitemap, verdict, identifiant Toque, version des règles de conversion. */
    private class Mark(val lastmod: String, val status: String, val id: String, val conversion: Int = Cookomix.CONVERSION) {
        override fun toString() = "$lastmod|$status|$id|$conversion"

        companion object {
            fun of(s: String?): Mark? {
                val p = s?.split('|') ?: return null
                return if (p.size < 3) null else Mark(p[0], p[1], p[2], p.getOrNull(3)?.toIntOrNull() ?: 1)
            }
        }
    }

    private class Stopped : Exception()

    private fun checkStop() {
        if (stop || !prefs.cookomixEnabled) throw Stopped()
    }

    /** Échec codé : les recettes déjà enregistrées restent, et le message le dit. */
    private fun failure(error: Cookomix.Error, detail: String = ""): Result {
        log.write("cookomix", "${error.code} $detail")
        return Result(false, tr("Recettes Cookomix non mises à jour, %s. Les recettes déjà enregistrées restent disponibles.", error.message(detail)))
    }

    private fun networkFailure(e: IOException): Result =
        if (e is HttpStatus) failure(Cookomix.Error.HTTP, tr("réponse %d du serveur", e.code))
        else failure(Cookomix.Error.NETWORK, e.message ?: e.javaClass.simpleName)

    private fun run(): Result {
        val withAdapted = prefs.cookomixAdapted
        val index = HashMap(readIndex())
        val before = HashMap(index)
        val batch = ArrayList<Recipe>()
        try {
            // 1. Liste des recettes (sitemaps). Liste vide ou anormalement courte : on s'arrête sans rien toucher.
            report(tr("Liste des recettes Cookomix…"))
            val entries = ArrayList<Cookomix.Entry>()
            try {
                val maps = Cookomix.recipeSitemaps(text(Cookomix.SITEMAP_INDEX))
                if (maps.isEmpty()) return failure(Cookomix.Error.LIST_UNREADABLE, tr("aucun sitemap de recettes"))
                for ((i, url) in maps.withIndex()) {
                    checkStop()
                    report(tr("Liste des recettes Cookomix… %d / %d", i + 1, maps.size))
                    // Un sitemap vide n'annonce pas des recettes retirées : c'est le site qui répond de travers.
                    val listed = Cookomix.parseSitemap(text(url))
                    if (listed.isEmpty()) return failure(Cookomix.Error.LIST_UNREADABLE, tr("sitemap sans recette : %s", url.substringAfterLast('/')))
                    entries += listed
                    pause(600)
                }
            } catch (e: IOException) {
                return networkFailure(e)
            }
            if (Cookomix.listLooksWrong(index.size, entries.size))
                return failure(Cookomix.Error.LIST_SHRUNK, tr("%d recettes au lieu de %d", entries.size, index.size))

            // 2. Pages nouvelles, modifiées, converties avec d'anciennes règles, ou absentes du catalogue.
            val present = repo.ids()
            fun wanted(m: Mark) = m.status == OK || (m.status == ADAPTED && withAdapted) ||
                ((m.status == UNREADABLE || m.status == ERROR) && m.id.isNotEmpty())
            val todo = entries.filter { e ->
                val m = Mark.of(index[e.slug])
                m == null || m.lastmod != e.lastmod || m.conversion != Cookomix.CONVERSION || m.status == ERROR || m.status == UNREADABLE ||
                    ((m.status == OK || m.status == ADAPTED) && wanted(m) && m.id !in present)
            }
            val breaker = Cookomix.Breaker()
            val touched = ArrayList<String>()
            var failures = 0
            var unreadable = 0
            var inARow = 0
            var shownOnce = false
            var unseen = 0
            fun save() {
                if (batch.isNotEmpty()) {
                    unseen += batch.size
                    // Les recettes apparaissent pendant la synchro (100 d'abord, puis tous les 500 : chaque
                    // rechargement relit tout le catalogue, ce qui coûte sur la tablette du robot).
                    val refresh = unseen >= if (shownOnce) 500 else 100
                    repo.upsertAll(ArrayList(batch), rebuild = refresh)
                    if (refresh) { unseen = 0; shownOnce = true }
                    batch.clear()
                }
                writeIndex(index)
            }
            for ((i, e) in todo.withIndex()) {
                checkStop()
                report(tr("Recettes Cookomix : %d / %d (déjà à jour : %d)", entries.size - todo.size + i + 1, entries.size, entries.size - todo.size))
                val previousId = Mark.of(index[e.slug])?.id.orEmpty()
                try {
                    val res = Cookomix.parsePage(text(e.url), e.url)
                    val r = res.recipe
                    val status = when (res.compat) {
                        Cookomix.Compat.OK -> OK
                        Cookomix.Compat.ADAPTED -> ADAPTED
                        Cookomix.Compat.INCOMPATIBLE -> INCOMPATIBLE
                        Cookomix.Compat.UNREADABLE -> UNREADABLE
                    }
                    index[e.slug] = Mark(e.lastmod, status, r?.id ?: previousId).toString()
                    touched += e.slug
                    if (r != null && (status == OK || (status == ADAPTED && withAdapted))) {
                        batch += r
                        photo(r.image)
                    }
                    when (status) {
                        INCOMPATIBLE -> log.write("cookomix", "${e.slug} : ${res.reasons.joinToString("; ")}")
                        UNREADABLE -> {
                            unreadable++
                            log.write("cookomix", "${Cookomix.Error.PAGES_UNREADABLE.code} ${e.slug} : ${res.reasons.joinToString("; ")}")
                        }
                    }
                    breaker.record(res.compat)
                    inARow = 0
                } catch (ex: IOException) {
                    failures++
                    inARow++
                    index[e.slug] = Mark(e.lastmod, ERROR, previousId).toString()
                    log.write("cookomix", "${e.slug} : ${ex.message}")
                    if (inARow >= 5) {
                        save()
                        repo.refresh()
                        return networkFailure(ex)
                    }
                }
                // Le site semble avoir changé : on annule les verdicts négatifs de cette synchro et on s'arrête.
                breaker.verdict()?.let { error ->
                    for (slug in touched) {
                        val status = Mark.of(index[slug])?.status
                        if (status == INCOMPATIBLE || status == UNREADABLE) {
                            val old = before[slug]
                            if (old == null) index.remove(slug) else index[slug] = old
                        }
                    }
                    save()
                    repo.refresh()
                    return failure(error, tr("%d pages lues", breaker.pages))
                }
                if (batch.size >= 25) save()
                pause(PAGE_PAUSE_MS)
            }
            save()

            // 3. Le catalogue Cookomix suit le site et le réglage « recettes adaptées » (une adaptée en favori reste).
            checkStop()
            index.keys.retainAll(entries.map { it.slug }.toHashSet())
            val keep = index.values.mapNotNull { Mark.of(it) }
                .filter { it.id.isNotEmpty() && (wanted(it) || (it.status == ADAPTED && repo.isFavorite(it.id))) }
                .map { it.id }.toHashSet()
            writeIndex(index)
            repo.removeCookomixExcept(keep)
            repo.refresh()

            // 4. Photos manquantes (en échec pendant la synchro).
            photos(keep)

            val marks = index.values.mapNotNull { Mark.of(it) }
            val ok = marks.count { it.status == OK }
            val adapted = marks.count { it.status == ADAPTED }
            val ko = marks.count { it.status == INCOMPATIBLE }
            var note = if (failures > 0) " " + tr("(%d en échec, réessayées à la prochaine synchro)", failures) else ""
            if (unreadable > 0) note += " " + tr("(%d illisibles, code %s, réessayées à la prochaine synchro)", unreadable, Cookomix.Error.PAGES_UNREADABLE.code)
            return Result(true, tr("Cookomix : %d compatibles, %d adaptées, %d incompatibles", ok, adapted, ko) + note, keep.size)
        } catch (_: Stopped) {
            // Arrêtée (bouton ou désactivation) : ce qui est lu reste, la suivante reprendra ici.
            if (prefs.cookomixEnabled) {
                if (batch.isNotEmpty()) repo.upsertAll(ArrayList(batch), rebuild = false)
                writeIndex(index)
                repo.refresh()
            }
            return Result(false, tr("Synchro Cookomix arrêtée : elle reprendra où elle en était."))
        }
    }

    // ------------------------------------------------------------------ photos

    /** Nom de fichier local d'une photo du site (« https://…/tarte.jpg » → « tarte.jpg »). */
    private fun photoName(url: String): String? =
        url.takeIf { it.startsWith("http") }?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotEmpty() }

    /** Photo d'une recette qui vient d'être lue ; un échec est rattrapé en fin de synchro. */
    private fun photo(url: String) {
        val name = photoName(url) ?: return
        if (repo.imageFile(name) != null) return
        try {
            savePhoto(url, name)
        } catch (e: Exception) {
            log.write("cookomix", "photo $url : $e")
        } catch (e: OutOfMemoryError) {
            log.write("cookomix", "photo $url : mémoire insuffisante")
        }
        pause(300)
    }

    private fun photos(ids: Set<String>) {
        val missing = repo.index.entries.filter { it.id in ids }.map { it.image }
            .filter { url -> photoName(url)?.let { repo.imageFile(it) == null } == true }
        var inARow = 0
        for ((i, url) in missing.withIndex()) {
            if (stop) return
            report(tr("Photos : %d / %d", i + 1, missing.size))
            try {
                savePhoto(url, photoName(url)!!)
                inARow = 0
            } catch (e: IOException) {
                log.write("cookomix", "photo $url : ${e.message}")
                if (++inARow >= 5) return
            } catch (e: Exception) {
                log.write("cookomix", "photo $url : $e")
            } catch (e: OutOfMemoryError) {
                log.write("cookomix", "photo $url : mémoire insuffisante")
            }
            pause(300)
        }
    }

    /** Photo réduite (800 px de large au plus), enregistrée sous le nom de fichier de l'URL. */
    private fun savePhoto(url: String, name: String) {
        val bytes = download(url, emptyMap(), 8 * 1024 * 1024)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException(tr("image illisible"))
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException(tr("image illisible"))
        if (bmp.width > MAX_WIDTH) {
            val scaled = Bitmap.createScaledBitmap(bmp, MAX_WIDTH, (bmp.height * MAX_WIDTH / bmp.width).coerceAtLeast(1), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        val tmp = File(repo.imagesDir, "$name.part")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        if (tmp.renameTo(File(repo.imagesDir, name))) repo.photoSaved(name) else tmp.delete()
    }

    // ------------------------------------------------------------------ réseau

    /** Le serveur a répondu, mais pas 200 (à distinguer d'un serveur injoignable). */
    private class HttpStatus(val code: Int) : IOException(tr("réponse %d du serveur", code))

    /** Page publique du site. */
    private fun text(url: String): String =
        String(download(url, mapOf("Accept-Language" to "fr-FR"), 4 * 1024 * 1024), Charsets.UTF_8)

    /** GET avec reprises espacées si le serveur demande de ralentir (429) ou est indisponible (5xx). */
    private fun download(url: String, headers: Map<String, String>, maxBytes: Int): ByteArray {
        var attempt = 0
        while (true) {
            attempt++
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", USER_AGENT)
                for ((k, v) in headers) c.setRequestProperty(k, v)
                val code = c.responseCode
                if ((code == 429 || code >= 500) && attempt < 4) {
                    pause(5_000L * attempt)
                    continue
                }
                if (code != 200) throw HttpStatus(code)
                c.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > maxBytes) throw IOException(tr("réponse trop grande"))
                    }
                    return out.toByteArray()
                }
            } finally {
                c.disconnect()
            }
        }
    }

    // ------------------------------------------------------------------ index

    private fun readIndex(): Map<String, String> = try {
        if (!indexFile.exists()) emptyMap()
        else Json.parse(indexFile.readText()).obj()["recipes"].obj().mapValues { (_, v) -> v?.toString().orEmpty() }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun writeIndex(index: Map<String, String>) {
        try {
            val tmp = File(indexFile.parentFile, indexFile.name + ".part")
            tmp.writeText(Json.write(linkedMapOf("recipes" to index)))
            if (!tmp.renameTo(indexFile)) {
                indexFile.delete()
                tmp.renameTo(indexFile)
            }
        } catch (e: Exception) {
            log.write("cookomix", "index non enregistré : $e")
        }
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    companion object {
        private const val OK = "ok"
        private const val ADAPTED = "adaptee"
        private const val INCOMPATIBLE = "incompatible"
        private const val ERROR = "erreur"
        private const val UNREADABLE = "illisible"
        /** Pause entre deux pages (plus le téléchargement et la photo) : une heure ou plus la première fois. */
        private const val PAGE_PAUSE_MS = 1_200L
        private const val MAX_WIDTH = 800
        /** Les requêtes vers cookomix.com nomment l'intégration Cookomix d'origine. */
        private const val USER_AGENT = "Toque (Android; Cookomix; +https://github.com/aguiran/MrCuisineConnectCookomix)"
    }
}
