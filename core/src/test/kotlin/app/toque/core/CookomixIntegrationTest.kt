package app.toque.core

import app.toque.core.companion.CatalogFilter
import app.toque.core.recipes.ChipSelection
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.RecipeChip
import app.toque.core.recipes.RecipeSource
import app.toque.core.robot.RecipeCompat
import app.toque.core.robot.RobotProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cookomix branché sur Toque 0.11 : source, pastille, filtre du compagnon, robots. */
class CookomixIntegrationTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().use { it.readText() }

    @Test fun connectOnly() {
        val connect = RobotProfiles.ALL.first { it.lidlDevice == Cookomix.CONNECT_DEVICE }
        assertEquals("Connect", connect.name)
        val r = Cookomix.parsePage(fixture("cookomix-page.html"), "https://www.cookomix.com/recettes/x/").recipe
        assertNotNull(r)
        assertEquals(RecipeSource.COOKOMIX, r!!.source)
        assertTrue(Cookomix.isCookomix(r.id))
        assertTrue(RecipeCompat.supports(r, connect))
        for (p in RobotProfiles.ALL.filter { it !== connect }) assertFalse(p.name, RecipeCompat.supports(r, p))
    }

    @Test fun chipAndFilter() {
        assertEquals(RecipeChip.Kind.COOKOMIX, RecipeChip.Kind.of("cookomix"))
        assertEquals(CatalogFilter.COOKOMIX, CatalogFilter.of(RecipeChip.COOKOMIX))
        val s = ChipSelection().tap(RecipeChip.MEALIE).tap(RecipeChip.COOKOMIX)
        assertTrue(s.cookomix)
        assertFalse(s.mealie)
        assertEquals(RecipeSource.COOKOMIX, s.filter.source)
        assertFalse(s.tap(RecipeChip.COOKOMIX).cookomix)
    }
}

class CookomixCommentsTest {
    private fun item(header: String, inner: String, photos: List<String> = emptyList()) =
        """<li class="activity new_note activity-item" id="activity-1"><div class="activity-content "><div class="activity-avatar"> <span class="cookl x1"><img src="a.gif" data-lazy-src="https://www.cookomix.com/wp-content/uploads/avatars/1/alice-bpthumb.jpg"></span></div>""" +
            """<div class="activity-header"><span class="ico icon-lames"></span><p><span class="cookl x1">Alice &#x1f370;</span> $header <span>il y a 2 mois</span></p></div>""" +
            """<div class="activity-inner"><p>$inner</p></div>""" +
            (if (photos.isEmpty()) "" else """<div class="mpp-container mpp-media-list">""" + photos.joinToString("") {
                """<figure data-src="$it.jpg"><img src="data:x" data-lazy-src="$it-480x320.jpg"></figure>"""
            } + "</div>") + """<div class="activity-meta clearfix"></div></div></li>"""

    @Test fun comments() {
        val page = "<html><ul>" +
            item("a noté 4/5 cette recette,", "Très bon&nbsp;!<br /> J&rsquo;ai mis moins de sel &#x1f60b;", listOf("https://www.cookomix.com/wp-content/uploads/a", "https://www.cookomix.com/wp-content/uploads/b")) +
            """<li class="activity-ad AdMB"><div class="ad-container"></div></li>""" +
            item("a posé une question sur cette recette,", "Peut-on congeler ?") +
            item("a commenté cette recette,", "Merci") +
            item("a commenté cette recette,", "") +
            "</ul></html>"
        val c = Cookomix.parseComments(page)
        assertEquals(3, c.size)
        assertEquals(Cookomix.CommentKind.RATED, c[0].kind)
        assertEquals(4, c[0].rating)
        assertEquals("Alice 🍰", c[0].author)
        assertEquals("il y a 2 mois", c[0].date)
        assertEquals("Très bon !\nJ’ai mis moins de sel 😋", c[0].text)
        assertEquals(listOf("https://www.cookomix.com/wp-content/uploads/a-480x320.jpg", "https://www.cookomix.com/wp-content/uploads/b-480x320.jpg"), c[0].photos)
        assertEquals("https://www.cookomix.com/wp-content/uploads/avatars/1/alice-bpthumb.jpg", c[0].avatar)
        assertEquals(Cookomix.CommentKind.QUESTION, c[1].kind)
        assertEquals(0, c[1].rating)
        assertEquals(Cookomix.CommentKind.COMMENT, c[2].kind)
        assertTrue(Cookomix.parseComments("<html>rien</html>").isEmpty())
    }

    @Test fun pageUrl() {
        val r = app.toque.core.recipes.Recipe(id = "ckx-1", title = "T", notes = "Intro\n\nSource : https://www.cookomix.com/recettes/tarte-thermomix/")
        assertEquals("https://www.cookomix.com/recettes/tarte-thermomix/", Cookomix.pageUrl(r))
    }

    @Test fun nextPages() {
        assertEquals("1520", Cookomix.scriptVersion("""<script>var CXdata = { "nonce": "0", "JSVer": "1520", };</script>"""))
        assertEquals("AbCdEf0123456789xyz", Cookomix.apiKey("""params.page=page}params.api_key="AbCdEf0123456789xyz";return params"""))
        assertEquals(null, Cookomix.apiKey("rien ici"))
        val r = app.toque.core.recipes.Recipe(id = "ckx-597776", title = "T")
        assertEquals(597776L, Cookomix.postId(r))
        assertEquals("https://www.cookomix.com/wp-json/cookomix/v1/activities/?secondary_id=597776&action=list_recipe_comment&page=2&api_key=K",
            Cookomix.activitiesUrl(597776, 2, "K"))
        val json = """{"page":2,"activities":[
            {"activity_id":"11","type":"new_note","note":"4","display_name":"Bob &#x1f60b;","content":"<p>Bon&nbsp;!</p>","time_since":"il y a 3 jours",
             "avatar":{"url":"https://www.cookomix.com/a/bob.jpg","letter":"B"},
             "photos":[{"thumb":"https://www.cookomix.com/p/1-480x320.jpg","large":"https://www.cookomix.com/p/1.jpg"}],
             "comments":[{"display_name":"Camille","content":"Merci Bob !","time_since":"il y a 2 jours","avatar":{"url":"https://www.cookomix.com/a/c.jpg"}}]},
            {"activity_id":12,"type":"new_question","note":"0","display_name":"Zoé","content":"Congélation ?","time_since":"il y a 1 an","avatar":{},"photos":[],"comments":[]},
            {"activity_id":13,"type":"new_comment","display_name":"X","content":"","photos":[],"comments":[]}]}"""
        val p = Cookomix.parseActivities(json)
        assertTrue(p.more)
        assertEquals(2, p.comments.size)
        val b = p.comments[0]
        assertEquals("11", b.id)
        assertEquals(Cookomix.CommentKind.RATED, b.kind)
        assertEquals(4, b.rating)
        assertEquals("Bob 😋", b.author)
        assertEquals("Bon !", b.text)
        assertEquals(listOf("https://www.cookomix.com/p/1-480x320.jpg"), b.photos)
        assertEquals("https://www.cookomix.com/a/bob.jpg", b.avatar)
        assertEquals("Camille", b.replies.single().author)
        assertEquals("Merci Bob !", b.replies.single().text)
        assertEquals("12", p.comments[1].id)
        assertEquals(Cookomix.CommentKind.QUESTION, p.comments[1].kind)
        assertFalse(Cookomix.parseActivities("""{"activities":[]}""").more)
        assertFalse(Cookomix.parseActivities("<html>erreur</html>").more)
    }
}
