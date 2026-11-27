package app.toque

import app.toque.core.i18n.I18n

// Réglages propres à ToqueIntegration (Cookomix, mises à jour automatiques), à part de Prefs.kt :
// les nouvelles versions de Wuki60 se fusionnent sans toucher à ce fichier.

/** Recettes de cookomix.com sur le robot (désactivé par défaut : première synchro d'une heure ou plus). */
var Prefs.cookomixEnabled: Boolean
    get() = p.getBoolean("cookomixEnabled", false)
    set(v) {
        p.edit().putBoolean("cookomixEnabled", v).commit()
    }

/** Garder aussi les recettes « adaptées » (un réglage modifié pour les limites du Connect). */
var Prefs.cookomixAdapted: Boolean
    get() = p.getBoolean("cookomixAdapted", true)
    set(v) {
        p.edit().putBoolean("cookomixAdapted", v).commit()
    }

/** Synchro Cookomix chaque nuit, avec les autres (désactivée par défaut : le site n'a pas d'API). */
var Prefs.cookomixNightly: Boolean
    get() = p.getBoolean("cookomixNightly", false)
    set(v) {
        p.edit().putBoolean("cookomixNightly", v).commit()
    }

var Prefs.cookomixLastMs: Long
    get() = p.getLong("cookomixLastMs", 0)
    set(v) {
        p.edit().putLong("cookomixLastMs", v).commit()
    }

/** Résultat de la dernière synchro Cookomix, écrit dans la langue d'alors (cookomixLastLang). */
var Prefs.cookomixLastResult: String
    get() = p.getString("cookomixLastResult", "") ?: ""
    set(v) {
        p.edit().putString("cookomixLastResult", v).putString("cookomixLastLang", I18n.code).commit()
    }

val Prefs.cookomixLastLang: String get() = p.getString("cookomixLastLang", "") ?: ""

/** Oublie l'état des synchros Cookomix (désactivation). */
fun Prefs.forgetCookomix() {
    p.edit().putBoolean("cookomixEnabled", false).remove("cookomixLastResult").remove("cookomixLastMs").commit()
}

/**
 * Installer seul une nouvelle version (celles de ce dépôt : Wuki60 + Cookomix), dès que le robot est
 * au calme, sans demander. Activé par défaut ; jamais pendant un programme, une recette ou une pesée.
 */
var Prefs.updateAuto: Boolean
    get() = p.getBoolean("updateAuto", true)
    set(v) {
        p.edit().putBoolean("updateAuto", v).commit()
    }
