package app.toque.cookomix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.Ui
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Onglet « Avis » d'une recette Cookomix : les avis de la page publique (« J'ai testé ! », questions),
 * avec la note et les photos des membres. Lus à l'ouverture de l'onglet, jamais enregistrés dans le
 * catalogue (ils changent souvent) ; la page et les miniatures restent en cache pendant la session.
 */
object CookomixReviews {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "toque-ckx-avis").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val cache = object : LinkedHashMap<String, List<Cookomix.Comment>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Cookomix.Comment>>?) = size > 20
    }
    private const val USER_AGENT = "Toque (Android; Cookomix; +https://github.com/aguiran/MrCuisineConnectCookomix)"
    private const val THUMB_PX = 480

    /** La recette a une page Cookomix connue : l'onglet « Avis » peut s'afficher. */
    fun available(r: Recipe): Boolean = Cookomix.isCookomix(r.id) && Cookomix.pageUrl(r) != null

    /** Remplit [box] (sous le titre déjà posé) ; [state] dit où en est le chargement. */
    fun show(a: MainActivity, recipe: Recipe, box: LinearLayout, state: TextView) {
        val url = Cookomix.pageUrl(recipe) ?: return
        state.visibility = View.VISIBLE
        state.text = tr("Chargement des avis…")
        exec.execute {
            val comments = try {
                synchronized(cache) { cache[url] } ?: Cookomix.parseComments(String(get(url, 4 * 1024 * 1024), Charsets.UTF_8))
                    .also { synchronized(cache) { cache[url] = it } }
            } catch (e: Exception) {
                null
            }
            box.post {
                when {
                    comments == null -> state.text = tr("Avis indisponibles (pas de connexion ?)")
                    comments.isEmpty() -> state.text = tr("Pas encore de commentaire sur cette recette.")
                    else -> {
                        state.visibility = View.GONE
                        for (cm in comments) box.addView(row(a, cm), Ui.lp(t = 14))
                        if (recipe.ratingCount > comments.size)
                            box.addView(Ui.text(a, tr("Les avis les plus utiles ; tous les autres sont sur cookomix.com."), 15f, Ui.TEXT3), Ui.lp(t = 12))
                    }
                }
            }
        }
    }

    private fun row(a: MainActivity, cm: Cookomix.Comment): View {
        val box = Ui.vertical(a)
        box.background = Ui.round(Ui.SURFACE2, 16f)
        box.setPadding(Ui.u(16), Ui.u(12), Ui.u(16), Ui.u(12))
        val head = Ui.horizontal(a)
        val badge = when (cm.kind) {
            Cookomix.CommentKind.RATED -> "★".repeat(cm.rating) + "☆".repeat(5 - cm.rating)
            Cookomix.CommentKind.QUESTION -> tr("Question")
            Cookomix.CommentKind.COMMENT -> tr("Commentaire")
        }
        head.addView(Ui.text(a, badge, 17f, Ui.ACCENT_INK), Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
        val who = listOf(shown(cm.author).ifBlank { tr("Anonyme") }, cm.date).filter { it.isNotBlank() }.joinToString(" · ")
        head.addView(Ui.ellipsize(Ui.text(a, who, 15f, Ui.TEXT3)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(head)
        val text = shown(cm.text)
        if (text.isNotBlank()) box.addView(Ui.text(a, text, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.12f) }, Ui.lp(t = 6))
        if (cm.photos.isNotEmpty()) {
            val strip = Ui.horizontal(a)
            for (p in cm.photos) {
                val img = ImageView(a).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = Ui.round(Ui.SURFACE3, 12f)
                    clipToOutline = true
                    setOnClickListener { enlarge(a, p) }
                }
                strip.addView(img, LinearLayout.LayoutParams(Ui.u(168), Ui.u(112)).apply { rightMargin = Ui.u(10) })
                photo(a, p, img)
            }
            box.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(strip) }, Ui.lp(t = 10))
        }
        return box
    }

    /** Photo touchée : en grand, par-dessus la fiche ; un appui à côté la ferme. */
    private fun enlarge(a: MainActivity, url: String) {
        val img = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true }
        val card = Ui.card(a, 28f, Ui.SURFACE2)
        card.setPadding(Ui.u(16), Ui.u(16), Ui.u(16), Ui.u(16))
        card.addView(img, LinearLayout.LayoutParams(Ui.MATCH, Ui.u(420)))
        card.setOnClickListener { a.closeOverlay() }
        a.showOverlay(card, 700)
        photo(a, url, img, Ui.u(660))
    }

    /** Miniature dans le cache de l'appli, puis affichée (sous-échantillonnée par [ImageLoader]). */
    private fun photo(a: MainActivity, url: String, view: ImageView, width: Int = Ui.u(168)) {
        val dir = File(a.cacheDir, "cookomix-avis").apply { mkdirs() }
        val file = File(dir, Integer.toHexString(url.hashCode()) + "-" + url.substringAfterLast('/').takeLast(60))
        if (file.length() > 0) { ImageLoader.load(view, file, width); return }
        exec.execute {
            try {
                val bytes = get(url, 2 * 1024 * 1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0) {
                    val tmp = File(dir, file.name + ".part")
                    if (bounds.outWidth <= THUMB_PX * 2) tmp.writeBytes(bytes) else {
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= THUMB_PX) sample *= 2
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        if (bmp != null) { FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }; bmp.recycle() }
                    }
                    if (tmp.renameTo(file)) view.post { ImageLoader.load(view, file, width) } else tmp.delete()
                }
            } catch (_: Exception) {
            } catch (_: OutOfMemoryError) {
            }
        }
    }

    /** Émojis trop récents pour la police d'Android 6 (carrés vides sur le robot) : retirés. */
    private val paint = Paint()

    private fun shown(s: String): String {
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val keep = when {
                // Sélecteurs de variante, liant et teintes de peau : sans effet sur une police qui ne les connaît pas.
                cp in 0xFE00..0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF -> false
                cp >= 0x1F000 || cp in 0x2600..0x27BF -> paint.hasGlyph(String(Character.toChars(cp)))
                else -> true
            }
            if (keep) out.appendCodePoint(cp)
            i += n
        }
        return out.toString().replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    private fun get(url: String, maxBytes: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Language", "fr-FR")
            if (c.responseCode != 200) throw IOException(tr("réponse %d du serveur", c.responseCode))
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
