package app.toque

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import app.toque.cookomix.CookomixReviews
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.recipes.Weigh
import app.toque.core.robot.RobotProfiles
import app.toque.machine.MachineService
import app.toque.ui.MainActivity
import app.toque.ui.screens.RecipeScreen
import app.toque.ui.screens.RecipesScreen
import app.toque.ui.screens.SettingsScreen
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Random

/**
 * Captures de l'intégration Cookomix (README, docs/images/cookomix/) : liste, fiche, étapes converties,
 * onglet Avis, visionneuse de photos, réglages. Recettes, avis et photos sont inventés pour l'essai
 * (rien n'est copié de cookomix.com) ; les photos sont dessinées ici. Rendu à 1024 × 600, l'écran du robot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr-rFR-w1024dp-h600dp-land-mdpi", application = ScreenshotApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CookomixScreensTest {
    private val outDir = File(System.getProperty("toque.screenshots") ?: "build/screenshots", "cookomix").apply { mkdirs() }
    private val suffix = if (ScreenshotApp.LIGHT) "-clair" else ""
    private lateinit var activity: MainActivity
    private var activityController: ActivityController<MainActivity>? = null
    private var serviceController: ServiceController<MachineService>? = null

    private val base = "https://www.cookomix.com/wp-content/uploads/essai/"
    private fun page(slug: String) = "https://www.cookomix.com/recettes/$slug/"

    private val risotto = Recipe(
        id = "ckx-900001", title = "Risotto crémeux aux champignons", category = "Plat principal", servings = 4,
        prepMinutes = 10, totalMinutes = 35, source = RecipeSource.COOKOMIX, language = "fr", devices = listOf(Cookomix.CONNECT_DEVICE),
        image = base + "risotto.jpg", author = "Camille L.", rating = 4.8, ratingCount = 126, tags = listOf("Cookomix"),
        notes = "Un risotto onctueux, prêt en 35 minutes.\n\nSource : " + page("risotto-champignons-thermomix"),
        ingredients = listOf(IngredientGroup(items = listOf(
            Ingredient("1", "", "échalote"), Ingredient("30", "g", "huile d'olive"), Ingredient("250", "g", "champignons de Paris"),
            Ingredient("300", "g", "riz arborio"), Ingredient("100", "g", "vin blanc sec"), Ingredient("800", "g", "bouillon de légumes"),
            Ingredient("40", "g", "parmesan râpé"), Ingredient("20", "g", "beurre"),
        ))),
        steps = listOf(
            Step("Mettre l'échalote dans le bol. Mixer.", StepMachine(StepProgram.MANUAL, 5, 5, 0), needsReview = true),
            Step("Racler les parois du bol avec la spatule."),
            Step("Ajouter l'huile d'olive.", weigh = Weigh("huile d'olive", 30)),
            Step("Faire revenir.", StepMachine(StepProgram.MANUAL, 180, 1, 120, reverse = true), needsReview = true),
            Step("Ajouter les champignons et le riz. Faire nacrer.", StepMachine(StepProgram.MANUAL, 120, 1, 100, reverse = true), needsReview = true),
            Step("Ajouter le vin blanc et le bouillon. Cuire.", StepMachine(StepProgram.MANUAL, 1080, 1, 100, reverse = true), Weigh("bouillon", 800), needsReview = true),
            Step("Ajouter le parmesan et le beurre. Mélanger. (Thermomix : 30 s / sens inverse / vitesse cuillère)",
                StepMachine(StepProgram.MANUAL, 30, 1, 0, reverse = true), needsReview = true),
        ),
    )
    private val brioche = Recipe(
        id = "ckx-900002", title = "Brioche tressée au sucre perlé", category = "Dessert", servings = 8, totalMinutes = 190,
        source = RecipeSource.COOKOMIX, language = "fr", devices = listOf(Cookomix.CONNECT_DEVICE), image = base + "brioche.jpg",
        author = "Julien P.", rating = 4.6, ratingCount = 88, tags = listOf("Cookomix", Cookomix.TAG_ADAPTED),
        notes = "Recette Thermomix adaptée au Monsieur Cuisine connect :\n• pétrissage de 6 min découpé en tranches de 2 min (limite du MCC)\n• vitesse 3,5 arrondie à 4\n• 98 °C arrondi à 95 °C\n\nSource : " + page("brioche-tressee-thermomix"),
        steps = listOf(Step("Pétrir.", StepMachine(StepProgram.KNEAD, 120), needsReview = true)),
    )
    private val others = listOf(
        cookomix("ckx-900003", "Velouté de butternut au lait de coco", "Entrée", 30, 4.9, 214, "veloute.jpg"),
        cookomix("ckx-900004", "Poulet au curry et riz basmati", "Plat principal", 40, 4.7, 301, "curry.jpg"),
        cookomix("ckx-900005", "Pâte à crêpes inratable", "Dessert", 5, 4.9, 512, "crepes.jpg"),
        cookomix("ckx-900006", "Houmous express", "Apéritif", 10, 4.5, 77, "houmous.jpg"),
        cookomix("ckx-900007", "Gratin dauphinois", "Accompagnement", 60, 4.8, 190, "gratin.jpg"),
        cookomix("ckx-900008", "Confiture de fraises", "Conserves", 35, 4.6, 64, "confiture.jpg"),
        cookomix("ckx-900009", "Pain de mie moelleux", "Pain", 150, 4.4, 58, "pain.jpg"),
    )

    private fun cookomix(id: String, title: String, cat: String, min: Int, rating: Double, n: Int, img: String) = Recipe(
        id = id, title = title, category = cat, servings = 4, totalMinutes = min, source = RecipeSource.COOKOMIX, language = "fr",
        devices = listOf(Cookomix.CONNECT_DEVICE), image = base + img, rating = rating, ratingCount = n, tags = listOf("Cookomix"),
        notes = "Source : " + page(id), steps = listOf(Step("Mixer.", StepMachine(StepProgram.MANUAL, 10, 5, 0), needsReview = true)),
    )

    private fun photos(prefix: String, n: Int) = (1..n).map { "${base}membres/$prefix-$it-480x320.jpg" }

    private val comments = listOf(
        Cookomix.Comment(Cookomix.CommentKind.RATED, "Sophie_cuisine", 5, "il y a 2 jours",
            "Testé ce midi, un vrai succès ! J'ai remplacé la moitié des champignons de Paris par des cèpes séchés réhydratés : " +
                "le goût est incroyable. Le riz est parfaitement crémeux, sans rien surveiller. Petit conseil : laisser reposer " +
                "deux minutes couvercle fermé avant de servir, et ajouter un filet d'huile de truffe au moment de servir. " +
                "Toute la famille a adoré, même ma fille qui ne mange jamais de champignons !",
            photos("sophie", 3), base + "avatars/sophie.jpg", "1",
            listOf(Cookomix.Reply("Camille L.", "Merci Sophie ! Excellente idée les cèpes, je l'ajoute en astuce dans la recette.", "il y a 1 jour"),
                Cookomix.Reply("Marc68", "Testé avec ton astuce : validé !", "il y a 5 heures"))),
        Cookomix.Comment(Cookomix.CommentKind.RATED, "Marc68", 5, "il y a 1 semaine",
            "Ma recette de risotto préférée, je la fais toutes les semaines. Parfaite avec un peu de persil frais.",
            photos("marc", 1), ""),
        Cookomix.Comment(Cookomix.CommentKind.QUESTION, "Lea.B", 0, "il y a 2 semaines",
            "Est-ce qu'on peut le préparer la veille et le réchauffer ? Merci pour vos retours !", emptyList(), base + "avatars/lea.jpg"),
        Cookomix.Comment(Cookomix.CommentKind.RATED, "Nono la gourmande", 4, "il y a 3 semaines",
            "Très bon, mais j'ai ajouté 100 g de bouillon en plus pour qu'il soit plus coulant.", photos("nono", 2), ""),
        Cookomix.Comment(Cookomix.CommentKind.COMMENT, "ChefPapa", 0, "il y a 1 mois",
            "Version végétarienne avec des petits pois et de la menthe : top pour le printemps.", photos("papa", 2), base + "avatars/papa.jpg"),
        Cookomix.Comment(Cookomix.CommentKind.RATED, "Isa_83", 5, "il y a 2 mois", "Simple, rapide et délicieux. Merci !", emptyList(), ""),
    )

    @After fun tearDown() {
        try { activityController?.pause()?.stop()?.destroy() } catch (_: Throwable) {}
        try { serviceController?.destroy() } catch (_: Throwable) {}
        idle(2)
    }

    private fun idle(rounds: Int = 1) {
        repeat(rounds) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitFor(what: String, timeoutMs: Long = 10_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            assertTrue("attente trop longue : $what", System.currentTimeMillis() < end)
            idle(); Thread.sleep(30)
        }
    }

    /** Laisse les images (fil d'ImageLoader) arriver avant la capture. */
    private fun settle() {
        repeat(25) { idle(); Thread.sleep(40) }
    }

    private fun shot(name: String) {
        settle()
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1024, 600)
        idle(2)
        val bmp = Bitmap.createBitmap(1024, 600, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun findText(v: View, text: String, exact: Boolean = true): TextView? {
        if (v is TextView && (if (exact) v.text.toString() == text else v.text.toString().startsWith(text)) && v.isShown) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), text, exact)?.let { return it }
        return null
    }

    private fun click(text: String, exact: Boolean = true) {
        var v: View = findText(activity.window.decorView, text, exact) ?: throw AssertionError("« $text » introuvable")
        while (!v.isClickable) v = v.parent as View
        v.performClick()
        idle(3)
    }

    private fun <T : View> find(v: View, type: Class<T>, ok: (T) -> Boolean = { true }): T? {
        if (type.isInstance(v) && v.isShown && ok(type.cast(v)!!)) return type.cast(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), type, ok)?.let { return it }
        return null
    }

    // ------------------------------------------------------------------ images inventées

    /** « Photo » dessinée : dégradé chaud, quelques formes floues, comme un plat vu de près. */
    private fun painted(seed: Int, w: Int, h: Int): Bitmap {
        val rnd = Random(seed.toLong())
        val palettes = arrayOf(
            intArrayOf(0xFFE8B07A.toInt(), 0xFF9C5A2E.toInt(), 0xFFF5E2C4.toInt()),
            intArrayOf(0xFFF2A65A.toInt(), 0xFFC4572A.toInt(), 0xFFFFE0A8.toInt()),
            intArrayOf(0xFFD9C29A.toInt(), 0xFF6E5236.toInt(), 0xFFF7F0E0.toInt()),
            intArrayOf(0xFFB9D08B.toInt(), 0xFF5D7A37.toInt(), 0xFFF0EBC8.toInt()),
            intArrayOf(0xFFE07A5F.toInt(), 0xFF8E2C2C.toInt(), 0xFFF6D1C1.toInt()),
        )
        val p = palettes[Math.abs(seed) % palettes.size]
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), p[0], p[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // Une assiette, puis des taches de couleur floues.
        paint.shader = RadialGradient(w * 0.5f, h * 0.55f, h * 0.48f, intArrayOf(p[2], p[2], Color.argb(0, 255, 255, 255)), floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(w * 0.5f, h * 0.55f, h * 0.48f, paint)
        repeat(14) {
            val x = w * (0.25f + rnd.nextFloat() * 0.5f)
            val y = h * (0.3f + rnd.nextFloat() * 0.5f)
            val r = h * (0.05f + rnd.nextFloat() * 0.12f)
            val col = p[rnd.nextInt(2)]
            paint.shader = RadialGradient(x, y, r, intArrayOf(col, Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, paint)
        }
        return bmp
    }

    private fun save(bmp: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    private fun start() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as ScreenshotApp
        val service = Robolectric.buildService(MachineService::class.java).create().also { serviceController = it }.get()
        shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, MachineService::class.java),
            service.onBind(Intent(app, MachineService::class.java)))
        app.prefs.robotId = RobotProfiles.MC_CONNECT.id
        RobotProfiles.active = RobotProfiles.MC_CONNECT
        app.prefs.cookomixEnabled = true
        app.prefs.cookomixNightly = true
        app.prefs.cookomixLastMs = System.currentTimeMillis()
        app.prefs.cookomixLastResult = "27/11 à 03:41 : Cookomix : 1752 compatibles, 685 adaptées, 0 incompatibles"
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().also { activityController = it }.get()
        waitFor("base simulée prête") { app.machine.snapshot.ready }

        val all = listOf(risotto, brioche) + others
        for ((i, r) in all.withIndex()) {
            val name = r.image.substringAfterLast('/')
            save(painted(i * 7 + 3, 800, 534), File(app.recipes.imagesDir, name))
            app.recipes.photoSaved(name)
        }
        app.recipes.upsertAll(all)
        waitFor("recettes Cookomix enregistrées") { app.recipes.index.entries.count { Cookomix.isCookomix(it.id) } == all.size }
        app.recipes.setFavorite(risotto.id, true)
        app.recipes.setFavorite("ckx-900005", true)

        CookomixReviews.preload(Cookomix.pageUrl(risotto)!!, comments)
        var k = 100
        for (cm in comments) {
            for (p in cm.photos) save(painted(k++, 480, 320), CookomixReviews.fileFor(app, p))
            if (cm.avatar.isNotEmpty()) save(painted(k++, 96, 96), CookomixReviews.fileFor(app, cm.avatar))
        }
        idle(3)
    }

    @Test fun cookomixScreens() {
        start()
        val app = org.robolectric.RuntimeEnvironment.getApplication() as ScreenshotApp
        activity.home()
        shot("01-accueil")

        activity.push(RecipesScreen(activity))
        idle(5)
        // La pastille « Cookomix » (dans la rangée qui défile), pas le badge d'une vignette.
        val chip = find(activity.window.decorView, HorizontalScrollView::class.java) { findText(it, "Cookomix") != null }
            ?.let { findText(it, "Cookomix") } ?: throw AssertionError("pastille Cookomix introuvable")
        var cv: View = chip
        while (!cv.isClickable) cv = cv.parent as View
        cv.performClick()
        idle(3)
        shot("02-liste-cookomix")

        activity.push(RecipeScreen(activity, app.recipes.get(risotto.id)!!))
        idle(3)
        assertNotNull(findText(activity.window.decorView, "Connect"))
        shot("03-fiche-ingredients")
        click("Étapes")
        shot("04-fiche-etapes")
        click("Avis")
        waitFor("avis affichés") { findText(activity.window.decorView, "Sophie_cuisine") != null }
        shot("05-avis")
        find(activity.window.decorView, ScrollView::class.java) { findText(it, "Sophie_cuisine") != null }?.scrollTo(0, 330)
        shot("06-avis-suite")
        click("Lire la suite")
        click("2 réponses")
        find(activity.window.decorView, ScrollView::class.java) { findText(it, "Sophie_cuisine") != null }?.scrollTo(0, 520)
        shot("07b-avis-reponses")
        find(activity.window.decorView, ScrollView::class.java) { findText(it, "Sophie_cuisine") != null }?.scrollTo(0, 330)
        shot("07-avis-lire-la-suite")
        click("Avec photos", exact = false)
        find(activity.window.decorView, ScrollView::class.java) { findText(it, "Sophie_cuisine") != null }?.scrollTo(0, 0)
        shot("08-avis-filtre-photos")
        // Première photo d'un avis, en grand.
        find(activity.window.decorView, ScrollView::class.java) { findText(it, "Sophie_cuisine") != null }?.scrollTo(0, 0)
        idle(2)
        find(activity.window.decorView, ImageView::class.java) { it.isClickable && it.width > 200 }!!.performClick()
        idle(3)
        shot("09-visionneuse")
        activity.closeOverlay()
        idle(2)
        find(activity.window.decorView, ScrollView::class.java) { findText(it, "Sophie_cuisine") != null }?.let { it.scrollTo(0, it.getChildAt(0).height) }
        shot("09b-avis-qr-code")

        activity.push(RecipeScreen(activity, app.recipes.get(brioche.id)!!))
        idle(3)
        shot("10-fiche-adaptee")
        click("Adaptée ›")
        shot("10b-details-adaptation")
        activity.closeOverlay()

        activity.push(SettingsScreen(activity, SettingsScreen.Family.RECIPES))
        idle(3)
        click("Cookomix")
        shot("11-reglages-cookomix")
        activity.push(SettingsScreen(activity, SettingsScreen.Family.UPDATES))
        idle(3)
        shot("12-reglages-mises-a-jour")
    }

    // ------------------------------------------------------------------ tour complet de l'appli

    private val tourDir = File(outDir.parentFile, "tour").apply { mkdirs() }
    private val failures = ArrayList<String>()

    /** Capture d'un écran du tour ; une erreur sur un écran n'arrête pas les autres. */
    private fun tourShot(name: String, open: () -> Unit) {
        try {
            activity.closeOverlay()
            open()
            idle(3)
            settle()
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1024, 600)
            idle(2)
            val bmp = Bitmap.createBitmap(1024, 600, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(tourDir, "$name$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            failures += "$name : $t"
        }
    }

    private fun settings(f: SettingsScreen.Family, name: String, scrolled: Boolean = true) {
        var screen: SettingsScreen? = null
        tourShot(name) { screen = SettingsScreen(activity, f).also { activity.push(it) } }
        if (scrolled) tourShot("$name-suite") { screen?.pane?.let { p -> p.scrollTo(0, p.getChildAt(0).height) } }
    }

    @Test fun tour() {
        start()
        val ta = org.robolectric.RuntimeEnvironment.getApplication() as ScreenshotApp
        val veloute = app.toque.core.recipes.Recipe(id = "perso-veloute", title = "Velouté de potimarron", category = "Soupes", servings = 4,
            totalMinutes = 35, source = RecipeSource.USER,
            ingredients = listOf(IngredientGroup(items = listOf(Ingredient("1", "", "oignon"), Ingredient("20", "g", "beurre"),
                Ingredient("600", "g", "potimarron"), Ingredient("500", "g", "eau"), Ingredient("100", "g", "crème")))),
            steps = listOf(Step("Éplucher l'oignon et couper le potimarron en morceaux."),
                Step("Mettre le beurre dans le bol.", weigh = Weigh("beurre", 20)),
                Step("Faire revenir l'oignon.", StepMachine(StepProgram.MANUAL, 180, 1, 100)),
                Step("Ajouter le potimarron et l'eau. Cuire.", StepMachine(StepProgram.MANUAL, 1200, 1, 100), Weigh("eau", 500)),
                Step("Ajouter la crème et mixer.", StepMachine(StepProgram.MANUAL, 60, 8, 0), Weigh("crème", 100))))
        ta.recipes.saveUser(veloute)
        waitFor("recette perso") { ta.recipes.index.entries.any { it.id == veloute.id } }
        ta.recipes.markCooked(risotto.id)
        ta.recipes.markCooked(veloute.id)

        tourShot("01-accueil") { activity.home() }
        tourShot("02-recettes") { activity.push(RecipesScreen(activity)) }
        tourShot("03-recherche") {
            activity.push(RecipesScreen(activity, focusSearch = true))
            idle(3)
            find(activity.window.decorView, android.widget.EditText::class.java)!!.setText("risoto")
            idle(5)
        }
        tourShot("04-recherche-ingredients") {
            find(activity.window.decorView, android.widget.EditText::class.java)!!.setText("champignons, riz")
            idle(5)
        }
        tourShot("05-favoris") { activity.push(RecipesScreen(activity, favorites = true)) }
        tourShot("06-recentes") { activity.push(RecipesScreen(activity, initialUsage = RecipesScreen.Usage.RECENT)) }
        tourShot("07-fiche-cookomix") { activity.push(RecipeScreen(activity, ta.recipes.get(risotto.id)!!)) }
        tourShot("08-fiche-etapes") { click("Étapes") }
        tourShot("09-fiche-perso") { activity.push(RecipeScreen(activity, ta.recipes.get(veloute.id)!!)) }
        tourShot("10-cuisson-guidee") { activity.push(app.toque.ui.screens.CookScreen(activity, ta.recipes.get(risotto.id)!!, 0)) }
        tourShot("11-cuisson-guidee-reglage") { activity.push(app.toque.ui.screens.CookScreen(activity, ta.recipes.get(risotto.id)!!, 3)) }
        tourShot("12-cuisson-guidee-pesee") { activity.push(app.toque.ui.screens.CookScreen(activity, ta.recipes.get(veloute.id)!!, 1)) }
        tourShot("13-recette-terminee") { activity.push(app.toque.ui.screens.RecipeDoneScreen(activity, risotto.id, risotto.title, 2100)) }
        tourShot("14-cuisson-manuelle") { activity.push(app.toque.ui.screens.ManualScreen(activity)) }
        tourShot("15-programmes") { activity.push(app.toque.ui.screens.ProgramsScreen(activity)) }
        tourShot("16-balance") { activity.push(app.toque.ui.screens.ScaleScreen(activity)) }
        tourShot("17-minuteurs") { activity.home(); idle(2); app.toque.ui.TimerPanel.open(activity) }
        tourShot("18-nouvelle-recette") { activity.push(app.toque.ui.screens.RecipeEditScreen(activity, app.toque.core.recipes.UserRecipes.blank(), true)) }
        tourShot("19-modifier-recette") { activity.push(app.toque.ui.screens.RecipeEditScreen(activity, ta.recipes.get(veloute.id)!!, false)) }

        settings(SettingsScreen.Family.SCREEN, "20-reglages-ecran")
        tourShot("21-roue-des-couleurs") { activity.push(app.toque.ui.screens.AccentWheelScreen(activity)) }
        settings(SettingsScreen.Family.KNOB_SOUNDS, "22-reglages-molette-sons")
        settings(SettingsScreen.Family.RECIPES, "23-reglages-recettes-lidl")
        for ((chip, n) in listOf("Cookomix" to "24", "toqueapp.org" to "25", "Mes recettes" to "26", "Appli d'origine" to "27"))
            tourShot("$n-reglages-recettes-${chip.lowercase().replace(' ', '-').replace("'", "").replace('.', '-')}") {
                activity.push(SettingsScreen(activity, SettingsScreen.Family.RECIPES)); idle(3); click(chip)
            }
        settings(SettingsScreen.Family.COOKING, "28-reglages-cuisson-balance")
        settings(SettingsScreen.Family.SAFETY, "29-reglages-securite")
        settings(SettingsScreen.Family.PHONE, "30-reglages-telephone", scrolled = false)
        tourShot("31-appairer-telephone") { activity.push(app.toque.ui.screens.PairingScreen(activity)) }
        settings(SettingsScreen.Family.LANGUAGE_TIME, "32-reglages-langue-heure")
        settings(SettingsScreen.Family.UPDATES, "33-reglages-mises-a-jour", scrolled = false)
        settings(SettingsScreen.Family.OTHERS, "34-reglages-autres", scrolled = false)
        settings(SettingsScreen.Family.ADVANCED, "35-reglages-avance")
        tourShot("36-diagnostic") { activity.push(app.toque.ui.screens.DiagnosticsScreen(activity)) }
        tourShot("37-journal") { activity.push(app.toque.ui.screens.LogScreen(activity)) }
        File(tourDir, "erreurs$suffix.txt").writeText(failures.joinToString("\n"))
    }
}
