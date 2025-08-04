package com.rayoai.presentation.ui.screens.home

import android.content.Intent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import android.speech.tts.TextToSpeech
import com.rayoai.R
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow

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

    LaunchedEffect(chatMessages) {
        val lastMessage = chatMessages.lastOrNull()
        val lastIndex = chatMessages.size - 1

        if (lastMessage != null && lastIndex >= 0) {
            val shouldFocus = when {
                lastMessage.isFromUser -> true
                !lastMessage.isFromUser && chatMessages.size == 2 -> {
                    textToSpeech?.speak(lastMessage.content, TextToSpeech.QUEUE_FLUSH, null, null)
                    true
                }
                !lastMessage.isFromUser -> {
                    textToSpeech?.speak(lastMessage.content, TextToSpeech.QUEUE_FLUSH, null, null)
                    true
                }
                else -> false
            }

            if (shouldFocus) {
                scope.launch {
                    listState.animateScrollToItem(lastIndex)
                    delay(100)
                    focusRequester.requestFocus()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        capturedImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = capturedImageDesc,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
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
                    ChatBubble(
                        message = message,
                        modifier = if (chatMessages.last() == message) Modifier.focusRequester(focusRequester) else Modifier
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

        if (selectedImageUris.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedImageUris) { uri ->
                    Box {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = "Selected image preview",
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
                            Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White)
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