package de.kalorienapp.ui.feedback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.BuildConfig
import de.kalorienapp.capture.ImageLoader
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.remote.firebase.FeedbackClient
import de.kalorienapp.domain.Feedback
import de.kalorienapp.domain.FeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class FeedbackUiState(
    val type: FeedbackType = FeedbackType.BUG,
    val text: String = "",
    val name: String = "",
    val screenshot: Bitmap? = null,
    val screenshotBase64: String? = null,
    val loadingImage: Boolean = false,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val failed: Boolean = false,
) {
    val canSend get() = !sending && !loadingImage && Feedback.canSend(text, screenshotBase64 != null)
}

class FeedbackViewModel(
    private val appContext: Context,
    private val client: FeedbackClient,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedbackUiState(name = settings.feedbackName))
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    fun setType(type: FeedbackType) = _state.update { it.copy(type = type, failed = false) }
    fun setText(text: String) = _state.update { it.copy(text = text, failed = false) }
    fun setName(name: String) = _state.update { it.copy(name = name.take(Feedback.MAX_NAME)) }
    /** „Noch etwas melden“: neues, leeres Formular (Name und Art bleiben). */
    fun again() = _state.update { FeedbackUiState(type = it.type, name = it.name) }

    fun removeScreenshot() = _state.update { it.copy(screenshot = null, screenshotBase64 = null) }

    /** Screenshot verkleinern, bis er sicher in ein Firestore-Dokument passt. */
    fun attach(uri: Uri) {
        _state.update { it.copy(loadingImage = true, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    var side = 1600
                    var bitmap = ImageLoader.load(appContext, uri, side)
                    var encoded: String
                    while (true) {
                        encoded = Base64.encodeToString(ImageLoader.toJpeg(bitmap, quality = 70), Base64.NO_WRAP)
                        if (encoded.length <= Feedback.MAX_SCREENSHOT_BASE64 || side <= 600) break
                        side -= 300
                        bitmap = ImageLoader.scaleDown(bitmap, side)
                    }
                    bitmap to encoded.takeIf { it.length <= Feedback.MAX_SCREENSHOT_BASE64 }
                }
            }.getOrNull()
            _state.update {
                if (result?.second == null) it.copy(loadingImage = false, failed = true)
                else it.copy(loadingImage = false, screenshot = result.first, screenshotBase64 = result.second)
            }
        }
    }

    fun send() {
        val s = _state.value
        if (!s.canSend) return
        _state.update { it.copy(sending = true, failed = false) }
        settings.setFeedbackName(s.name.trim())
        viewModelScope.launch {
            val ok = runCatching {
                client.send(
                    Feedback(
                        type = s.type,
                        text = s.text,
                        name = s.name,
                        screenshotJpegBase64 = s.screenshotBase64,
                        appVersion = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidSdk = Build.VERSION.SDK_INT,
                        createdAt = Instant.now(),
                    ),
                )
            }.isSuccess
            _state.update { if (ok) it.copy(sending = false, sent = true) else it.copy(sending = false, failed = true) }
        }
    }
}
