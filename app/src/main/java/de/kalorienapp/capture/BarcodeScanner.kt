package de.kalorienapp.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Sucht lokal (on-device, ML Kit) nach Produkt-Barcodes im Foto. */
internal class BarcodeScanner {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build(),
    )

    /** @return Barcode-Ziffern oder null, wenn keiner gefunden wurde (auch bei Scanner-Fehlern). */
    suspend fun scan(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                cont.resume(barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) })
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
