package app.toque.cookomix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import app.toque.ui.Icon
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
 * Onglet « Avis » d'une recette Cookomix : la note du site, la galerie des photos des membres
 * (visionneuse plein écran), des filtres (avec photos, notes, questions) et les avis de la page
 * publique (« J'ai testé ! », commentaires, questions) avec avatar, note, date et photos.
 *
 * Lus à l'ouverture de l'onglet, jamais enregistrés dans le catalogue (ils changent souvent) ; la
 * page et les miniatures restent en cache pendant la session (miniatures : dans le cache d'Android).
 */
object CookomixReviews {
    private val exec = Executors.newFixedThreadPool(2) { r -> Thread(r, "toque-ckx-avis").apply { priority = Thread.NORM_PRIORITY - 1 } }
    /** Les avis lus pour une recette : ceux de la page, puis ceux des pages suivantes demandées. */
    private class Reviews(val comments: MutableList<Cookomix.Comment>, val scriptVersion: String?) {
        var nextPage = 2
        var more = true
        var loading = false
        var filter = Filter.ALL
    }

    private val cache = object : LinkedHashMap<String, Reviews>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Reviews>?) = size > 20
    }
    /** Clé du script des avis du site, lue une fois par session (jamais écrite dans le dépôt). */
    @Volatile private var apiKey: String? = null
    private const val USER_AGENT = "Toque (Android; Cookomix; +https://github.com/aguiran/MrCuisineConnectCookomix)"
    private const val THUMB_PX = 480
    /** Au-delà, l'avis est replié sur [FOLDED_LINES] lignes avec « Lire la suite ». */
    private const val FOLD_CHARS = 280
    private const val FOLDED_LINES = 5
    private const val GALLERY_MAX = 30

    private enum class Filter { ALL, PHOTOS, RATED, QUESTIONS }

    /** Une photo de la galerie et qui l'a postée. */
    private class Shot(val url: String, val author: String, val date: String, val rating: Int)

    /** La recette a une page Cookomix connue : l'onglet « Avis » peut s'afficher. */
    fun available(r: Recipe): Boolean = Cookomix.isCookomix(r.id) && Cookomix.pageUrl(r) != null

    /** Essais d'écran : avis d'une page sans réseau. */
    internal fun preload(pageUrl: String, comments: List<Cookomix.Comment>) = synchronized(cache) { cache[pageUrl] = Reviews(comments.toMutableList(), null) }

    /** Fichier du cache où une image du site est gardée (essais d'écran : images posées à l'avance). */
    internal fun fileFor(c: Context, url: String): File =
        File(File(c.cacheDir, "cookomix-avis").apply { mkdirs() }, Integer.toHexString(url.hashCode()) + "-" + url.substringAfterLast('/').takeLast(60))

    /** Remplit [box] (sous le titre déjà posé) ; [state] dit où en est le chargement. */
    fun show(a: MainActivity, recipe: Recipe, box: LinearLayout, state: TextView) {
        val url = Cookomix.pageUrl(recipe) ?: return
        val cached = synchronized(cache) { cache[url] }
        if (cached != null) { render(a, recipe, box, state, cached); return }
        state.visibility = View.VISIBLE
        state.text = tr("Chargement des avis…")
        exec.execute {
            val comments = try {
                val html = String(get(url, 4 * 1024 * 1024), Charsets.UTF_8)
                Reviews(Cookomix.parseComments(html).toMutableList(), Cookomix.scriptVersion(html)).also { synchronized(cache) { cache[url] = it } }
            } catch (e: Exception) {
                null
            }
            box.post {
                if (comments == null) state.text = tr("Avis indisponibles (pas de connexion ?)")
                else render(a, recipe, box, state, comments)
            }
        }
    }

    // ------------------------------------------------------------------ onglet

    /** Marque du contenu de l'onglet (refait quand des avis suivants arrivent). */
    private const val CONTENT_TAG = "cookomix-avis"

    private fun render(a: MainActivity, recipe: Recipe, box: LinearLayout, state: TextView, st: Reviews) {
        val comments: List<Cookomix.Comment> = ArrayList(st.comments)
        box.findViewWithTag<View>(CONTENT_TAG)?.let { box.removeView(it) }
        if (comments.isEmpty()) {
            state.visibility = View.VISIBLE
            state.text = tr("Pas encore de commentaire sur cette recette.")
            return
        }
        state.visibility = View.GONE
        val outer = box
        @Suppress("NAME_SHADOWING") val box = Ui.vertical(a).apply { tag = CONTENT_TAG }
        outer.addView(box)
        val shots = comments.flatMap { cm -> cm.photos.map { Shot(it, shown(cm.author), cm.date, cm.rating) } }.take(GALLERY_MAX)
        box.addView(summary(a, recipe, comments, shots.size), Ui.lp(t = 12))
        if (shots.isNotEmpty()) {
            box.addView(label(a, tr("Photos des membres") + " · " + shots.size), Ui.lp(t = 18))
            val strip = Ui.horizontal(a)
            for ((i, s) in shots.withIndex()) {
                val img = thumb(a, 14f) { viewer(a, shots, i) }
                strip.addView(img, LinearLayout.LayoutParams(Ui.u(138), Ui.u(104)).apply { rightMargin = Ui.u(10) })
                photo(a, s.url, img, Ui.u(138))
            }
            box.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(strip) }, Ui.lp(t = 10))
        }

        val list = Ui.vertical(a)
        val chips = Ui.horizontal(a)
        var filter = st.filter
        fun count(f: Filter) = comments.count { matches(it, f) }
        fun fill() {
            chips.removeAllViews()
            for (f in Filter.values()) {
                val n = count(f)
                if (f != Filter.ALL && n == 0) continue
                val name = when (f) {
                    Filter.ALL -> tr("Tous")
                    Filter.PHOTOS -> tr("Avec photos")
                    Filter.RATED -> tr("Notes")
                    Filter.QUESTIONS -> tr("Questions")
                }
                chips.addView(Ui.toggleChip(a, "$name  $n", f == filter) { filter = f; st.filter = f; fill() }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
            }
            list.removeAllViews()
            for (cm in comments.filter { matches(it, filter) }) list.addView(row(a, cm), Ui.lp(t = 12))
        }
        fill()
        box.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, Ui.lp(t = 20))
        box.addView(list, Ui.lp(t = 4))
        val postId = Cookomix.postId(recipe)
        if (st.more && postId != null) {
            val more = Ui.button(a, if (st.loading) tr("Chargement des avis…") else tr("Voir plus d'avis (%d affichés)", comments.size),
                Ui.Style.SECONDARY, Icon.Kind.DOWNLOAD, height = 52, textSize = 17f) { loadMore(a, recipe, outer, state, st, postId) }
            Ui.setButtonEnabled(more, !st.loading)
            box.addView(more, Ui.lp(Ui.WRAP, Ui.WRAP, t = 16))
        }
        box.addView(allOnPhone(a, Cookomix.pageUrl(recipe)!!, st.more || recipe.ratingCount > comments.size), Ui.lp(t = 16, b = 8))
    }

    /**
     * Page suivante des avis, par l'interface que les pages du site appellent en faisant défiler. Sa clé est
     * lue dans le script des avis du site (une fois par session). Une page sans avis nouveau : c'est la fin.
     */
    private fun loadMore(a: MainActivity, recipe: Recipe, box: LinearLayout, state: TextView, st: Reviews, postId: Long) {
        if (st.loading) return
        st.loading = true
        render(a, recipe, box, state, st)
        exec.execute {
            val page = try {
                val key = apiKey ?: run {
                    val version = st.scriptVersion ?: Cookomix.scriptVersion(String(get(Cookomix.pageUrl(recipe)!!, 4 * 1024 * 1024), Charsets.UTF_8))
                        ?: throw IOException("version des scripts introuvable")
                    Cookomix.apiKey(String(get(Cookomix.activitiesScriptUrl(version), 2 * 1024 * 1024), Charsets.UTF_8))
                        ?: throw IOException("clé introuvable")
                }.also { apiKey = it }
                Cookomix.parseActivities(String(get(Cookomix.activitiesUrl(postId, st.nextPage, key), 2 * 1024 * 1024, Cookomix.ACTIVITIES_HEADERS), Charsets.UTF_8))
            } catch (e: Exception) {
                // Clé changée par le site : relue à la prochaine demande.
                if (e is IOException && e.message?.contains("40") == true) apiKey = null
                null
            }
            box.post {
                st.loading = false
                if (page == null) {
                    a.toast(tr("Avis suivants indisponibles pour l'instant"), Ui.WARN)
                } else {
                    val seen = st.comments.map { it.id.ifEmpty { it.author + "|" + it.text.take(40) } }.toHashSet()
                    val fresh = page.comments.filter { seen.add(it.id.ifEmpty { it.author + "|" + it.text.take(40) }) }
                    st.comments += fresh
                    st.nextPage++
                    if (!page.more || fresh.isEmpty()) st.more = false
                    if (fresh.isEmpty()) a.toast(tr("Tous les avis sont affichés"))
                }
                render(a, recipe, box, state, st)
            }
        }
    }

    /**
     * Un QR code ouvre la recette sur le téléphone, à la section des avis (pour laisser le sien).
     */
    private fun allOnPhone(a: MainActivity, url: String, more: Boolean): View {
        val row = Ui.horizontal(a)
        row.background = Ui.round(Ui.SURFACE2, 18f)
        row.setPadding(Ui.u(16), Ui.u(14), Ui.u(16), Ui.u(14))
        val code = try { app.toque.core.qr.QrCode.encode(url.trimEnd('/') + "/#avis", app.toque.core.qr.QrCode.ErrorCorrection.L) } catch (_: Throwable) { null }
        if (code != null) row.addView(app.toque.ui.QrView(a).apply { this.code = code }, LinearLayout.LayoutParams(Ui.u(112), Ui.u(112)).apply { rightMargin = Ui.u(16) })
        val col = Ui.vertical(a)
        col.addView(Ui.text(a, tr("Sur votre téléphone"), 18f, Ui.TEXT, Ui.MEDIUM))
        col.addView(Ui.text(a, tr("Scannez ce code pour ouvrir les avis de cette recette sur cookomix.com, et y laisser le vôtre avec vos photos."),
            15f, Ui.TEXT2).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 4))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        return row
    }

    private fun matches(cm: Cookomix.Comment, f: Filter) = when (f) {
        Filter.ALL -> true
        Filter.PHOTOS -> cm.photos.isNotEmpty()
        Filter.RATED -> cm.rating > 0
        Filter.QUESTIONS -> cm.kind == Cookomix.CommentKind.QUESTION
    }

    /** Note du site en grand, et ce que contient la page (avis, photos, questions). */
    private fun summary(a: MainActivity, recipe: Recipe, comments: List<Cookomix.Comment>, photos: Int): View {
        val card = Ui.horizontal(a)
        card.background = Ui.round(Ui.SURFACE2, 20f)
        card.setPadding(Ui.u(20), Ui.u(16), Ui.u(20), Ui.u(16))
        val left = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
        val avg = if (recipe.ratingCount > 0) recipe.rating else comments.filter { it.rating > 0 }.map { it.rating }.average().takeIf { !it.isNaN() } ?: 0.0
        left.addView(Ui.text(a, if (avg > 0) String.format(I18n.locale, "%.1f", avg) else "–", 40f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }, Ui.lp())
        left.addView(Ui.text(a, stars(avg), 18f, Ui.ACCENT_INK).apply { setSingleLine() })
        left.addView(Ui.text(a, if (recipe.ratingCount > 0) tr("%d notes", recipe.ratingCount) else tr("Pas encore noté"), 14f, Ui.TEXT3), Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
        card.addView(left, Ui.lp(Ui.u(118), Ui.WRAP, r = 18))
        val bars = Ui.vertical(a)
        val rated = comments.filter { it.rating > 0 }
        for (n in 5 downTo 1) {
            val k = rated.count { it.rating == n }
            val line = Ui.horizontal(a)
            line.addView(Ui.text(a, "$n★", 14f, Ui.TEXT2), Ui.lp(Ui.u(34), Ui.WRAP))
            val track = FrameLayout(a)
            track.background = Ui.round(Ui.SURFACE3, 6f)
            val fill = View(a).apply { background = Ui.round(Ui.ACCENT, 6f) }
            val frac = if (rated.isEmpty()) 0f else k.toFloat() / rated.size
            track.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
            track.post { fill.layoutParams = FrameLayout.LayoutParams((track.width * frac).toInt(), FrameLayout.LayoutParams.MATCH_PARENT); fill.requestLayout() }
            line.addView(track, LinearLayout.LayoutParams(0, Ui.u(10), 1f))
            line.addView(Ui.text(a, k.toString(), 14f, Ui.TEXT3).apply { gravity = Gravity.END }, Ui.lp(Ui.u(30), Ui.WRAP))
            bars.addView(line, Ui.lp(t = if (n == 5) 0 else 5))
        }
        card.addView(bars, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val facts = Ui.vertical(a)
        facts.addView(fact(a, Icon.Kind.STAR, tr("%d avis", comments.size)))
        facts.addView(fact(a, Icon.Kind.SCREEN, tr("%d photos", photos)), Ui.lp(t = 6))
        val q = comments.count { it.kind == Cookomix.CommentKind.QUESTION }
        if (q > 0) facts.addView(fact(a, Icon.Kind.INFO, if (q == 1) tr("1 question") else tr("%d questions", q)), Ui.lp(t = 6))
        card.addView(facts, Ui.lp(Ui.WRAP, Ui.WRAP, l = 22))
        return card
    }

    private fun fact(a: MainActivity, icon: Icon.Kind, text: String): View {
        val row = Ui.horizontal(a)
        row.addView(Icon(a, icon, Ui.TEXT2), LinearLayout.LayoutParams(Ui.u(18), Ui.u(18)).apply { rightMargin = Ui.u(8) })
        row.addView(Ui.text(a, text, 15f, Ui.TEXT2))
        return row
    }

    private fun stars(v: Double): String {
        val full = Math.round(v).toInt().coerceIn(0, 5)
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    private fun label(a: MainActivity, t: String): TextView =
        Ui.text(a, t.uppercase(), 14f, Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.08f }

    /** Un avis : avatar, nom, note ou nature, date ; texte replié s'il est long ; ses photos. */
    private fun row(a: MainActivity, cm: Cookomix.Comment): View {
        val box = Ui.vertical(a)
        box.background = Ui.round(Ui.SURFACE2, 18f)
        box.setPadding(Ui.u(16), Ui.u(14), Ui.u(16), Ui.u(14))
        val head = Ui.horizontal(a)
        val author = shown(cm.author).ifBlank { tr("Anonyme") }
        head.addView(avatar(a, cm, author), LinearLayout.LayoutParams(Ui.u(46), Ui.u(46)).apply { rightMargin = Ui.u(12) })
        val who = Ui.vertical(a)
        who.addView(Ui.ellipsize(Ui.text(a, author, 17f, Ui.TEXT, Ui.MEDIUM)))
        val line = Ui.horizontal(a)
        when (cm.kind) {
            Cookomix.CommentKind.RATED -> line.addView(Ui.text(a, stars(cm.rating.toDouble()), 16f, Ui.ACCENT_INK))
            Cookomix.CommentKind.QUESTION -> line.addView(Ui.chip(a, tr("Question"), Icon.Kind.INFO, Ui.ACCENT_INK, Ui.ACCENT_DIM, 13f))
            Cookomix.CommentKind.COMMENT -> line.addView(Ui.chip(a, tr("Commentaire"), null, Ui.TEXT2, Ui.SURFACE3, 13f))
        }
        if (cm.date.isNotBlank()) line.addView(Ui.text(a, cm.date, 14f, Ui.TEXT3), Ui.lp(Ui.WRAP, Ui.WRAP, l = 10))
        who.addView(line, Ui.lp(t = 2))
        head.addView(who, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(head)

        val text = shown(cm.text)
        if (text.isNotBlank()) {
            val body = Ui.text(a, text, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.14f) }
            box.addView(body, Ui.lp(t = 10))
            if (text.length > FOLD_CHARS) {
                body.maxLines = FOLDED_LINES
                body.ellipsize = TextUtils.TruncateAt.END
                val more = Ui.text(a, tr("Lire la suite"), 16f, Ui.ACCENT_INK, Ui.MEDIUM)
                more.setPadding(0, Ui.u(8), Ui.u(20), Ui.u(4))
                more.isClickable = true
                more.setOnClickListener {
                    val open = body.maxLines == FOLDED_LINES
                    body.maxLines = if (open) Int.MAX_VALUE else FOLDED_LINES
                    more.text = if (open) tr("Réduire") else tr("Lire la suite")
                }
                box.addView(more, Ui.lp(Ui.WRAP, Ui.WRAP))
            }
        }
        for (r in cm.replies) box.addView(reply(a, r), Ui.lp(t = 10))
        if (cm.photos.isNotEmpty()) {
            val shots = cm.photos.map { Shot(it, author, cm.date, cm.rating) }
            val strip = Ui.horizontal(a)
            for ((i, p) in cm.photos.withIndex()) {
                val img = thumb(a, 12f) { viewer(a, shots, i) }
                strip.addView(img, LinearLayout.LayoutParams(Ui.u(150), Ui.u(100)).apply { rightMargin = Ui.u(10) })
                photo(a, p, img, Ui.u(150))
            }
            box.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(strip) }, Ui.lp(t = 12))
        }
        return box
    }

    /** Une réponse à un avis, en retrait, avec un filet à gauche. */
    private fun reply(a: MainActivity, r: Cookomix.Reply): View {
        val row = Ui.horizontal(a, Gravity.TOP)
        row.addView(View(a).apply { background = Ui.round(Ui.STROKE, 2f) }, LinearLayout.LayoutParams(Ui.u(3), LinearLayout.LayoutParams.MATCH_PARENT).apply { leftMargin = Ui.u(20); rightMargin = Ui.u(12) })
        val author = shown(r.author).ifBlank { tr("Anonyme") }
        row.addView(avatarOf(a, author, r.avatar, 34), LinearLayout.LayoutParams(Ui.u(34), Ui.u(34)).apply { rightMargin = Ui.u(10) })
        val col = Ui.vertical(a)
        val head = Ui.horizontal(a)
        head.addView(Ui.ellipsize(Ui.text(a, author, 15f, Ui.TEXT, Ui.MEDIUM)))
        if (r.date.isNotBlank()) head.addView(Ui.text(a, r.date, 13f, Ui.TEXT3), Ui.lp(Ui.WRAP, Ui.WRAP, l = 8))
        col.addView(head)
        col.addView(Ui.text(a, shown(r.text), 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 2))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        return row
    }

    /** Photo de profil ronde, ou ses initiales sur une couleur tirée de son nom. */
    private fun avatar(a: MainActivity, cm: Cookomix.Comment, author: String): View = avatarOf(a, author, cm.avatar, 46)

    private fun avatarOf(a: MainActivity, author: String, url: String, sizeDp: Int): View {
        val frame = FrameLayout(a)
        frame.background = Ui.gradient(Ui.placeholderColors(author), sizeDp / 2f)
        frame.clipToOutline = true
        frame.addView(Ui.text(a, Ui.initials(author).take(2), sizeDp * 0.37f, 0xFFFFFFFF.toInt(), Ui.MEDIUM).apply { gravity = Gravity.CENTER },
            Ui.flp(gravity = Gravity.CENTER))
        if (url.isNotEmpty()) {
            val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            frame.addView(img, Ui.flp())
            photo(a, url, img, Ui.u(sizeDp))
        }
        return frame
    }

    private fun thumb(a: MainActivity, radius: Float, onClick: () -> Unit): ImageView = ImageView(a).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = Ui.round(Ui.SURFACE3, radius)
        clipToOutline = true
        isClickable = true
        setOnClickListener { onClick() }
    }

    // ------------------------------------------------------------------ visionneuse

    /** Photos en grand, une à la fois : précédente, suivante, qui l'a postée ; un appui à côté ferme. */
    private fun viewer(a: MainActivity, shots: List<Shot>, start: Int) {
        var i = start
        val card = Ui.card(a, 26f, Ui.SURFACE)
        card.setPadding(Ui.u(14), Ui.u(14), Ui.u(14), Ui.u(12))
        val img = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = Ui.round(0xFF000000.toInt(), 18f); clipToOutline = true }
        card.addView(img, LinearLayout.LayoutParams(Ui.MATCH, Ui.u(430)))
        val bar = Ui.horizontal(a)
        val prev = Ui.button(a, "‹", Ui.Style.SECONDARY, height = 52, textSize = 26f) {}
        val next = Ui.button(a, "›", Ui.Style.SECONDARY, height = 52, textSize = 26f) {}
        val caption = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
        val who = Ui.text(a, "", 17f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        val where = Ui.text(a, "", 14f, Ui.TEXT3).apply { gravity = Gravity.CENTER }
        caption.addView(who); caption.addView(where)
        bar.addView(prev, Ui.lp(Ui.u(76), Ui.WRAP))
        bar.addView(caption, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        bar.addView(next, Ui.lp(Ui.u(76), Ui.WRAP))
        bar.addView(Ui.button(a, "", Ui.Style.GHOST, Icon.Kind.CLOSE, height = 52) { a.closeOverlay() }, Ui.lp(Ui.WRAP, Ui.WRAP, l = 8))
        card.addView(bar, Ui.lp(t = 12))
        fun showAt(k: Int) {
            i = (k + shots.size) % shots.size
            val s = shots[i]
            who.text = (if (s.rating > 0) stars(s.rating.toDouble()) + "  " else "") + s.author
            where.text = listOf(s.date, "${i + 1} / ${shots.size}").filter { it.isNotBlank() }.joinToString(" · ")
            photo(a, s.url, img, Ui.u(820))
            Ui.setButtonEnabled(prev, shots.size > 1)
            Ui.setButtonEnabled(next, shots.size > 1)
        }
        prev.setOnClickListener { showAt(i - 1) }
        next.setOnClickListener { showAt(i + 1) }
        img.setOnClickListener { showAt(i + 1) }
        a.showOverlay(card, 860)
        showAt(start)
    }

    // ------------------------------------------------------------------ images et réseau

    /** Image du site dans le cache de l'appli, puis affichée (sous-échantillonnée par [ImageLoader]). */
    private fun photo(a: MainActivity, url: String, view: ImageView, width: Int) {
        val file = fileFor(a, url)
        if (file.length() > 0) { ImageLoader.load(view, file, width); return }
        view.tag = url
        view.setImageDrawable(null)
        exec.execute {
            try {
                val bytes = get(url, 3 * 1024 * 1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0) {
                    val tmp = File(file.parentFile, file.name + ".part")
                    if (bounds.outWidth <= THUMB_PX * 2) tmp.writeBytes(bytes) else {
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= THUMB_PX) sample *= 2
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        if (bmp != null) { FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }; bmp.recycle() }
                    }
                    if (tmp.renameTo(file)) view.post { if (view.tag == url) ImageLoader.load(view, file, width) } else tmp.delete()
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
            val keep = when {
                // Sélecteurs de variante, liant et teintes de peau : sans effet sur une police qui ne les connaît pas.
                cp in 0xFE00..0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF -> false
                cp >= 0x1F000 || cp in 0x2600..0x27BF -> paint.hasGlyph(String(Character.toChars(cp)))
                else -> true
            }
            if (keep) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString().replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    private fun get(url: String, maxBytes: Int, headers: Map<String, String> = emptyMap()): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Language", "fr-FR")
            for ((k, v) in headers) c.setRequestProperty(k, v)
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
