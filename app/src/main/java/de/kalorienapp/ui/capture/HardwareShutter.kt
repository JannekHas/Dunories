package de.kalorienapp.ui.capture

/**
 * Foto per Lauter-/Leiser-Taste wie in der Kamera-App (Wunsch des Nutzers: Handy in einer Hand, Essen in der
 * anderen). Solange die Kamera sichtbar ist, setzt sie [onPress]; `MainActivity` leitet die Tasten dorthin um
 * (und ändert dann nicht die Lautstärke).
 */
object HardwareShutter {
    @Volatile
    var onPress: (() -> Unit)? = null
}
