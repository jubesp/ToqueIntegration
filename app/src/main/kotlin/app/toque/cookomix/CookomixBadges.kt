package app.toque.cookomix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Ui

/**
 * Ce qui signale une recette Cookomix : le logo du site (à la place de « Depuis cookomix.com »), et
 * un signe qui dit si la recette passe telle quelle sur le Monsieur Cuisine Connect ou si un réglage
 * a été adapté (un appui en donne la liste).
 */
object CookomixBadges {
    @Volatile private var logo: Bitmap? = null

    /** Logo de cookomix.com (assets/cookomix/logo.png, 642 × 132), ou null s'il manque. */
    fun logo(c: Context): Bitmap? = logo ?: try {
        // Les assets de l'APK ; à défaut (essais d'écran hors APK), le même fichier sur le chemin des classes.
        val input = try { c.assets.open("cookomix/logo.png") } catch (_: Exception) { javaClass.classLoader?.getResourceAsStream("assets/cookomix/logo.png") }
        input?.use { BitmapFactory.decodeStream(it) }?.also { logo = it }
    } catch (_: Exception) {
        null
    }

    /** Un réglage a été changé pour le Monsieur Cuisine (mot-clé « Adaptée MCC »). */
    fun adapted(tags: List<String>): Boolean = Cookomix.TAG_ADAPTED in tags

    /** Texte du badge des vignettes : « Cookomix », et « adaptée » seulement quand un réglage a changé. */
    fun cardBadge(tags: List<String>): String = if (adapted(tags)) tr("Cookomix · adaptée") else "Cookomix"

    /**
     * En-tête de la fiche, sobre : le logo et l'auteur ; puis la note et le signe sur une seule ligne
     * (« ★ 4,8 · 126 notes · ✓ Connect », ou « Adaptée › » qui ouvre le détail).
     */
    fun header(a: MainActivity, r: Recipe): View {
        val col = Ui.vertical(a)
        val row = Ui.horizontal(a)
        val bmp = logo(a)
        if (bmp != null) {
            val img = ImageView(a).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_START; adjustViewBounds = true }
            img.contentDescription = "Cookomix"
            row.addView(img, LinearLayout.LayoutParams(Ui.u(20) * bmp.width / bmp.height, Ui.u(20)))
        } else row.addView(Ui.text(a, "cookomix", 17f, 0xFF539332.toInt(), Ui.MEDIUM))
        if (r.author.isNotBlank()) row.addView(Ui.ellipsize(Ui.text(a, tr("par %s", r.author), 16f, Ui.TEXT2)), Ui.lp(Ui.WRAP, Ui.WRAP, l = 10))
        col.addView(row)

        val line = Ui.horizontal(a)
        if (r.ratingCount > 0) {
            line.addView(Ui.text(a, "★ " + String.format(app.toque.core.i18n.I18n.locale, "%.1f", r.rating), 16f, Ui.ACCENT_INK, Ui.MEDIUM))
            line.addView(Ui.text(a, " · " + tr("%d notes", r.ratingCount) + " · ", 16f, Ui.TEXT2))
        }
        val changes = Cookomix.adaptations(r)
        if (adapted(r.tags) || changes.isNotEmpty()) {
            val t = Ui.text(a, tr("Adaptée ›"), 16f, Ui.ACCENT_INK, Ui.MEDIUM)
            t.isClickable = true
            t.setPadding(0, Ui.u(6), Ui.u(12), Ui.u(6))
            t.setOnClickListener { details(a, changes) }
            line.addView(t)
        } else {
            line.addView(Icon(a, Icon.Kind.CHECK, Ui.OK), LinearLayout.LayoutParams(Ui.u(16), Ui.u(16)).apply { rightMargin = Ui.u(4) })
            line.addView(Ui.text(a, "Connect", 16f, Ui.OK, Ui.MEDIUM))
        }
        col.addView(line, Ui.lp(t = 4))
        return col
    }

    /** Ce qui a été changé, réglage par réglage ; le réglage Thermomix d'origine reste rappelé dans l'étape. */
    private fun details(a: MainActivity, changes: List<String>) {
        val card = Ui.card(a, 26f, Ui.SURFACE)
        card.setPadding(Ui.u(28), Ui.u(24), Ui.u(28), Ui.u(22))
        val head = Ui.horizontal(a)
        head.addView(Icon(a, Icon.Kind.EDIT, Ui.ACCENT_INK), LinearLayout.LayoutParams(Ui.u(28), Ui.u(28)).apply { rightMargin = Ui.u(12) })
        head.addView(Ui.text(a, tr("Recette adaptée au Monsieur Cuisine"), 22f, Ui.TEXT, Ui.MEDIUM))
        card.addView(head)
        card.addView(Ui.text(a, tr("Écrite pour le Thermomix : ces réglages ont été changés pour rester dans les limites du Monsieur Cuisine Connect. Le réglage d'origine est rappelé dans chaque étape concernée."),
            16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.12f) }, Ui.lp(t = 10))
        val list = changes.ifEmpty { listOf(tr("réglage adapté (détail indisponible : resynchronisez Cookomix)")) }
        for (ch in list) {
            val row = Ui.horizontal(a, android.view.Gravity.TOP)
            row.addView(Ui.text(a, "•", 18f, Ui.ACCENT_INK), Ui.lp(Ui.u(22), Ui.WRAP))
            row.addView(Ui.text(a, ch.replaceFirstChar { it.uppercase() }, 18f, Ui.TEXT).apply { setLineSpacing(0f, 1.1f) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            card.addView(row, Ui.lp(t = 10))
        }
        card.addView(Ui.button(a, tr("OK"), Ui.Style.PRIMARY, height = 52, textSize = 18f) { a.closeOverlay() }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 18))
        a.showOverlay(card, 700)
    }
}
