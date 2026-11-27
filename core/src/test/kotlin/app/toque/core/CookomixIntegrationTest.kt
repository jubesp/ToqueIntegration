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
