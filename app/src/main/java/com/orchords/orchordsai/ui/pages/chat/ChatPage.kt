package com.orchords.orchordsai.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.orchords.ai.provider.BuiltInTools
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.ui.UIMessagePart
import me.orchid.hugeicons.HugeIcons
import me.orchid.hugeicons.stroke.Cancel01
import me.orchid.hugeicons.stroke.LeftToRightListBullet
import me.orchid.hugeicons.stroke.Menu03
import me.orchid.hugeicons.stroke.MessageAdd01
import com.orchords.orchordsai.R
import com.orchords.orchordsai.data.ai.planning.isPlanningModeEnabled
import com.orchords.orchordsai.data.ai.planning.withPlanningMode
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.datastore.findProvider
import com.orchords.orchordsai.data.datastore.getCurrentAssistant
import com.orchords.orchordsai.data.datastore.getCurrentChatModel
import com.orchords.orchordsai.data.files.FilesManager
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.Conversation
import com.orchords.orchordsai.data.model.ConversationRetention
import com.orchords.orchordsai.data.model.TemporaryConversationRegistry
import com.orchords.orchordsai.data.repository.WorkspaceRepository
import com.orchords.orchordsai.service.ChatError
import com.orchords.orchordsai.ui.components.ai.ChatAttachmentPickerActions
import com.orchords.orchordsai.ui.components.ai.ChatInput
import com.orchords.orchordsai.ui.components.ai.FilesPicker
import com.orchords.orchordsai.ui.components.ai.SearchMode
import com.orchords.orchordsai.ui.components.ai.completion.WorkspaceCompletionProvider
import com.orchords.orchordsai.ui.components.ai.rememberChatAttachmentPickerActions
import com.orchords.orchordsai.ui.context.LocalNavController
import com.orchords.orchordsai.ui.context.LocalToaster
import com.orchords.orchordsai.ui.context.Navigator
import com.orchords.orchordsai.ui.hooks.ChatInputState
import com.orchords.orchordsai.ui.hooks.EditStateContent
import com.orchords.orchordsai.ui.hooks.useEditState
import com.orchords.orchordsai.utils.base64Decode
import com.orchords.orchordsai.utils.navigateToChatPage
import com.orchords.orchordsai.utils.navigateToTemporaryChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val isTemporary = TemporaryConversationRegistry.isTemporary(id.toString())
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    LaunchedEffect(isTemporary, conversation.retention) {
        if (isTemporary && conversation.retention != ConversationRetention.TEMPORARY) {
            vm.updateConversation(conversation.copy(retention = ConversationRetention.TEMPORARY))
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    LaunchedEffect(drawerState.isOpen) { if (drawerState.isOpen) softwareKeyboardController?.hide() }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen = windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp
    LaunchedEffect(isBigScreen) { if (isBigScreen && drawerState.isOpen) drawerState.close() }

    val inputState = vm.inputState
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { filesManager.getFileMimeType(it) }
            inputState.messageContent = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) add(UIMessagePart.Image(url = file.toString()))
                    else if (type?.startsWith("video/") == true) add(UIMessagePart.Video(url = file.toString()))
                    else if (type?.startsWith("audio/") == true) add(UIMessagePart.Audio(url = file.toString()))
                }
            }
        }
        text?.base64Decode()?.takeIf { it.isNotEmpty() }?.let(inputState::setMessageText)
    }

    val chatListState = rememberLazyListState()
    var followPolicy by remember(id) { mutableStateOf(ViewportFollowPolicy(id.toString())) }
    LaunchedEffect(conversation.id) { followPolicy = followPolicy.onConversationChanged(conversation.id.toString()).policy }
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) chatListState.scrollToItem(index)
            } else chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            vm.chatListInitialized = true
        }
    }

    val content: @Composable () -> Unit = {
        ChatPageContent(
            inputState = inputState,
            loadingJob = loadingJob,
            processingStatus = processingStatus,
            setting = setting,
            conversation = conversation,
            drawerState = drawerState,
            navController = navController,
            vm = vm,
            chatListState = chatListState,
            enableWebSearch = enableWebSearch,
            currentChatModel = currentChatModel,
            bigScreen = isBigScreen,
            errors = errors,
            onDismissError = { vm.dismissError(it) },
            onClearAllErrors = { vm.clearAllErrors() },
            followPolicy = followPolicy,
            onFollowPolicyChange = { followPolicy = it },
            temporary = isTemporary,
        )
    }

    if (isBigScreen) {
        PermanentNavigationDrawer(
            drawerContent = {
                ChatDrawerContent(
                    navController = navController,
                    vm = vm,
                    settings = setting,
                    current = conversation,
                )
            },
            content = content,
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ChatDrawerContent(
                    navController = navController,
                    vm = vm,
                    settings = setting,
                    current = conversation,
                )
            },
            content = content,
        )
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    followPolicy: ViewportFollowPolicy,
    onFollowPolicyChange: (ViewportFollowPolicy) -> Unit,
    temporary: Boolean,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val assistant = setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(WorkspaceCompletionProvider(workspaceId.toString(), workspaceRepository, conversation.workspaceCwd))
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    temporary = temporary,
                    onNewChat = { navigateToChatPage(navController) },
                    onNewTemporaryChat = { navigateToTemporaryChatPage(navController) },
                    onClickMenu = { previewMode = !previewMode },
                    onUpdateTitle = vm::updateTitle,
                )
            },
            bottomBar = {
                ChatInput(
                    state = inputState,
                    loading = loadingJob != null,
                    settings = setting,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    onCancelClick = vm::stopGeneration,
                    enableSearch = enableWebSearch,
                    onUpdateSearchMode = { mode ->
                        val current = setting.getCurrentAssistant()
                        val model = setting.getCurrentChatModel()
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == current.id) assistant.copy(enableWebSearch = mode == SearchMode.LOCAL) else assistant
                                },
                                providers = if (model == null) setting.providers else setting.providers.map { provider ->
                                    provider.editModel(model.copy(tools = if (mode == SearchMode.BUILT_IN) model.tools + BuiltInTools.Search else model.tools - BuiltInTools.Search))
                                },
                            )
                        )
                    },
                    planningModeEnabled = conversation.modeInjectionIds.isPlanningModeEnabled(),
                    onUpdatePlanningMode = { enabled ->
                        vm.updateConversation(conversation.copy(modeInjectionIds = conversation.modeInjectionIds.withPlanningMode(enabled)))
                        vm.saveConversationAsync()
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("Select a model first", type = ToastType.Error)
                            return@ChatInput
                        }
                        onFollowPolicyChange(followPolicy.onSend())
                        if (inputState.isEditing()) vm.handleMessageEdit(parts = inputState.getContents(), messageId = inputState.editingMessage!!)
                        else {
                            vm.handleMessageSend(inputState.getContents())
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        onFollowPolicyChange(followPolicy.onSend())
                        if (inputState.isEditing()) vm.handleMessageEdit(parts = inputState.getContents(), messageId = inputState.editingMessage!!)
                        else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch { chatListState.requestScrollToItem(conversation.currentMessages.size + 5) }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = { vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it) },
                    onUpdateAssistant = { updated ->
                        vm.updateSettings(setting.copy(assistants = setting.assistants.map { if (it.id == updated.id) updated else it }))
                    },
                    onUpdateSearchService = { index -> vm.updateSettings(setting.copy(searchServiceSelected = index)) },
                    onMoreClick = { showFilesSheet = true },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                followPolicy = followPolicy,
                onFollowPolicyChange = onFollowPolicyChange,
                onRegenerate = vm::regenerateAtMessage,
                onEdit = { inputState.editingMessage = it.id; inputState.setContents(it.parts) },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = { if (loadingJob != null) vm.showDeleteBlockedWhileGeneratingError() else vm.deleteMessage(it) },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(conversation.copy(messageNodes = conversation.messageNodes.map { if (it.id == newNode.id) newNode else it }))
                    vm.saveConversationAsync()
                },
                onClickSuggestion = { inputState.editingMessage = null; inputState.setMessageText(it) },
                onTranslate = vm::translateMessage,
                onClearTranslation = { vm.clearTranslationField(it.id) },
                onJumpToMessage = { index -> previewMode = false; scope.launch { chatListState.requestScrollToItem(index) } },
                onToolApproval = vm::handleToolApproval,
                onToolAnswer = vm::handleToolAnswer,
                onToggleFavorite = vm::toggleMessageFavorite,
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                    vm.saveConversationAsync()
                },
            )
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onDismiss: () -> Unit,
) {
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    fun dismissAll() { showInjectionSheet = false; showCompressDialog = false; onDismiss() }

    val filesSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(sheetState = filesSheetState, onDismissRequest = ::dismissAll) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = { updated ->
                vm.updateSettings(setting.copy(assistants = setting.assistants.map { if (it.id == updated.id) updated else it }))
            },
            onUpdateConversation = { vm.updateConversation(it); vm.saveConversationAsync() },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = ::dismissAll,
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    temporary: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onNewTemporaryChat: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String>(onUpdateTitle)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(HugeIcons.Menu03, "Messages") }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = { if (conversation.messageNodes.isNotEmpty()) titleState.open(conversation.title) else toaster.show(editTitleWarning, type = ToastType.Warning) },
                color = Color.Transparent,
            ) {
                Column {
                    val assistant = settings.getCurrentAssistant()
                    val model = settings.getCurrentChatModel()
                    val provider = model?.findProvider(settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { if (temporary) stringResource(R.string.chat_page_temporary_chat) else stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (temporary) {
                        Text(stringResource(R.string.chat_page_temporary_not_saved), maxLines = 1, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                    } else if (model != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onClickMenu) { Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options") }
            if (!temporary) IconButton(onClick = onNewTemporaryChat) { Text(stringResource(R.string.chat_page_temporary_badge), style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = onNewChat) { Icon(HugeIcons.MessageAdd01, "New Message") }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = titleState::dismiss,
            title = { Text(stringResource(R.string.chat_page_edit_title)) },
            text = { OutlinedTextField(value = title, onValueChange = onUpdate, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { TextButton(onClick = titleState::confirm) { Text(stringResource(R.string.chat_page_save)) } },
            dismissButton = { TextButton(onClick = titleState::dismiss) { Text(stringResource(R.string.chat_page_cancel)) } },
        )
    }
}
