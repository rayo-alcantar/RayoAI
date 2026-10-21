package com.rayoai.presentation.ui.screens.home

import android.util.Log

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.PermissionState
import com.rayoai.domain.model.ChatMessage
import com.rayoai.presentation.ui.components.ChatBubble
import com.rayoai.presentation.ui.components.VoiceInputButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import android.speech.tts.TextToSpeech
import com.rayoai.R
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatSection(
    capturedImage: Bitmap?,
    imageUri: Uri?,
    chatMessages: List<ChatMessage>,
    isAiTyping: Boolean,
    isLoading: Boolean,
    onSaveImage: (Bitmap) -> Unit,
    onShareImage: (Uri) -> Unit,
    onSendMessage: (String) -> Unit,
    isTranscribingAudio: Boolean,
    onVoiceMessageReady: (com.rayoai.domain.model.AudioRecording) -> Unit,
    textToSpeech: TextToSpeech?,
    ttsInitialized: Boolean,
    writeStoragePermissionState: PermissionState,
    onError: (String) -> Unit,
    selectedImageUris: List<Uri>,
    onAddImageRequest: () -> Unit,
    onRemoveSelectedImage: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val capturedImageDesc = stringResource(R.string.captured_image_description)
    val openFullscreenImageDesc = stringResource(R.string.open_fullscreen_image)
    val saveImageDesc = stringResource(R.string.save_image)
    val noImageToSave = context.getString(R.string.no_image_to_save)
    val shareImageDesc = stringResource(R.string.share_image)
    val noImageToShare = context.getString(R.string.no_image_to_share)
    val shareImageChooser = stringResource(R.string.share_image_chooser)
    val analyzingImageDesc = stringResource(R.string.analyzing_image)
    val aiTypingText = stringResource(R.string.ai_typing)
    val askAboutImageLabel = stringResource(R.string.ask_about_image_label)
    val sendMessageDesc = stringResource(R.string.send_message)
    val loadMorePhotosDesc = stringResource(R.string.load_more_photos_icon_description)
    val transcribingAudioText = stringResource(R.string.scan_pdf_transcribing_audio)
    val selectedImagePreviewDesc = stringResource(R.string.selected_image_preview)
    val removeImageDesc = stringResource(R.string.remove_image)
    var showFullScreenImage by remember { mutableStateOf(false) }

    LaunchedEffect(chatMessages, listState) {
        val lastIndex = chatMessages.indices.last
        if (lastIndex < 0) return@LaunchedEffect

        // Always try to scroll to the last item and focus.
        // TTS is NOT triggered automatically here; the user activates it on demand from ChatBubble.
        try {
            launch {
                listState.animateScrollToItem(lastIndex)
                delay(100)
                focusRequester.requestFocus()
            }
        } catch (e: Exception) {
            Log.w("ChatSection", "Failed to scroll or focus on new message", e)
        }
    }

    if (showFullScreenImage && capturedImage != null) {
        FullScreenZoomableImageDialog(
            bitmap = capturedImage,
            imageDescription = capturedImageDesc,
            onDismiss = { showFullScreenImage = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        capturedImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = openFullscreenImageDesc,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable(
                        onClickLabel = openFullscreenImageDesc,
                        role = Role.Button
                    ) {
                        showFullScreenImage = true
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = openFullscreenImageDesc
                    },
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    capturedImage?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || writeStoragePermissionState.status.isGranted) {
                            onSaveImage(it)
                        } else {
                            writeStoragePermissionState.launchPermissionRequest()
                        }
                    } ?: onError(noImageToSave)
                },
                enabled = capturedImage != null
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = saveImageDesc
                )
            }

            IconButton(
                onClick = {
                    imageUri?.let { uri ->
                        onShareImage(uri)
                    } ?: onError(noImageToShare)
                },
                enabled = imageUri != null
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = shareImageDesc
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = analyzingImageDesc
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages) { message ->
                    val isLastAiMessage = chatMessages.last() == message && !message.isFromUser
                    val baseModifier = if (chatMessages.last() == message) Modifier.focusRequester(focusRequester) else Modifier
                    val accessibilityModifier = if (isLastAiMessage) {
                        baseModifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    } else {
                        baseModifier
                    }
                    ChatBubble(
                        message = message,
                        modifier = accessibilityModifier
                    )
                }
            }
        }

        if (isAiTyping) {
            Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
                Text(
                    text = aiTypingText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (isTranscribingAudio) {
            Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
                Text(
                    text = transcribingAudioText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (selectedImageUris.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedImageUris) { uri ->
                    Box {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = selectedImagePreviewDesc,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemoveSelectedImage(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .padding(2.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = removeImageDesc, tint = Color.White)
                        }
                    }
                }
            }
        }

        var chatInput by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                label = { Text(askAboutImageLabel) },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
                ),
                maxLines = 5
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAddImageRequest,
                enabled = selectedImageUris.size < 3
            ) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = loadMorePhotosDesc
                )
            }
            VoiceInputButton(
                enabled = !isLoading && !isAiTyping,
                isTranscribingAudio = isTranscribingAudio,
                onRecordingReady = onVoiceMessageReady,
                onError = onError
            )
            IconButton(onClick = {
                if (chatInput.isNotBlank() || selectedImageUris.isNotEmpty()) {
                    onSendMessage(chatInput)
                    chatInput = ""
                }
            }, enabled = chatInput.isNotBlank() || selectedImageUris.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = sendMessageDesc
                )
            }
        }
    }
}

@Composable
private fun FullScreenZoomableImageDialog(
    bitmap: Bitmap,
    imageDescription: String,
    onDismiss: () -> Unit
) {
    val closeDesc = stringResource(R.string.close_fullscreen_image)
    val zoomInDesc = stringResource(R.string.zoom_in_image)
    val zoomOutDesc = stringResource(R.string.zoom_out_image)
    val zoomResetDesc = stringResource(R.string.zoom_reset_image)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomPercent = (scale * 100f).roundToInt()
    val zoomLabel = stringResource(R.string.zoom_level_label, zoomPercent)

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || containerSize.width == 0 || containerSize.height == 0) {
            return Offset.Zero
        }
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    fun updateScale(newScale: Float) {
        val coerced = newScale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
        scale = coerced
        offset = clampOffset(offset, coerced)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = imageDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                val nextScale = when {
                                    scale < 2f -> 2.5f
                                    scale < 4f -> 4.5f
                                    else -> 1f
                                }
                                updateScale(nextScale)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
                            scale = newScale
                            offset = clampOffset(offset + pan, newScale)
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .semantics {
                        contentDescription = imageDescription
                        stateDescription = zoomLabel
                    }
            )

            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .semantics { contentDescription = closeDesc }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { updateScale(scale - ZOOM_STEP) },
                        enabled = scale > MIN_ZOOM_SCALE,
                        modifier = Modifier.semantics { contentDescription = zoomOutDesc }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = null
                        )
                    }

                    Text(
                        text = zoomLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = zoomLabel
                        }
                    )

                    IconButton(
                        onClick = { updateScale(1f) },
                        enabled = scale > MIN_ZOOM_SCALE || offset != Offset.Zero,
                        modifier = Modifier.semantics { contentDescription = zoomResetDesc }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null
                        )
                    }

                    IconButton(
                        onClick = { updateScale(scale + ZOOM_STEP) },
                        enabled = scale < MAX_ZOOM_SCALE,
                        modifier = Modifier.semantics { contentDescription = zoomInDesc }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 6f
private const val ZOOM_STEP = 0.75f
