package com.orchords.orchordsai.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orchords.orchordsai.R
import com.orchords.orchordsai.Screen
import com.orchords.orchordsai.ui.components.nav.BackButton
import com.orchords.orchordsai.ui.components.ui.AutoAIIcon
import com.orchords.orchordsai.ui.components.ui.FormItem
import com.orchords.orchordsai.ui.components.ui.OutlinedNumberInput
import com.orchords.orchordsai.ui.components.ui.Tag
import com.orchords.orchordsai.ui.components.ui.TagType
import com.orchords.orchordsai.ui.context.LocalNavController
import com.orchords.orchordsai.ui.theme.CustomColors
import com.orchords.orchordsai.utils.plus
import com.orchords.search.SearchCommonOptions
import com.orchords.search.SearchService
import com.orchords.search.SearchServiceOptions
import me.orchid.hugeicons.HugeIcons
import me.orchid.hugeicons.stroke.Add01
import me.orchid.hugeicons.stroke.PencilEdit01
import org.koin.androidx.compose.koinViewModel

/**
 * Search-tool settings. Search providers are external tool infrastructure and
 * do not change the first-party oai-1.0 chat-model route.
 *
 * Only adapters with a currently verified runtime contract are presented.
 * Legacy serialized provider records are normalized out of this active list so
 * a retired provider cannot look selectable while executing somewhere else.
 */
@Composable
fun SettingSearchPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val nav = LocalNavController.current
    var showAddDialog by remember { mutableStateOf(false) }

    val supportedServices = remember(settings.searchServices) {
        settings.searchServices.filter(SearchService::isRuntimeSupported)
            .ifEmpty { listOf(SearchServiceOptions.DEFAULT) }
    }
    val selectedServiceId = settings.searchServices
        .getOrNull(settings.searchServiceSelected)
        ?.takeIf(SearchService::isRuntimeSupported)
        ?.id
    val normalizedSelected = supportedServices.indexOfFirst { it.id == selectedServiceId }
        .takeIf { it >= 0 } ?: 0
    val needsNormalization =
        supportedServices != settings.searchServices ||
            settings.searchServiceSelected != normalizedSelected

    LaunchedEffect(supportedServices, normalizedSelected, needsNormalization) {
        if (needsNormalization) {
            vm.updateSettings(
                settings.copy(
                    searchServices = supportedServices,
                    searchServiceSelected = normalizedSelected,
                )
            )
        }
    }

    val canAddProvider = supportedServices.any { it is SearchServiceOptions.OrchordsAIOptions }.not() ||
        supportedServices.any { it is SearchServiceOptions.BraveOptions }.not()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_search_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    if (canAddProvider) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                imageVector = HugeIcons.Add01,
                                contentDescription = stringResource(R.string.setting_page_search_add_provider),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(supportedServices, key = { _, service -> service.id }) { _, service ->
                SearchProviderCard(
                    service = service,
                    onEdit = {
                        nav.navigate(Screen.SettingSearchDetail(service.id.toString()))
                    },
                )
            }

            item("common_options") {
                CommonOptions(
                    settings = settings,
                    onUpdate = { options ->
                        vm.updateSettings(settings.copy(searchCommonOptions = options))
                    },
                )
            }
        }
    }

    if (showAddDialog) {
        val choices = buildList<SearchServiceOptions> {
            if (supportedServices.none { it is SearchServiceOptions.OrchordsAIOptions }) {
                add(SearchServiceOptions.OrchordsAIOptions())
            }
            if (supportedServices.none { it is SearchServiceOptions.BraveOptions }) {
                add(SearchServiceOptions.BraveOptions())
            }
        }
        AddSearchProviderDialog(
            choices = choices,
            onDismiss = { showAddDialog = false },
            onConfirm = { service ->
                showAddDialog = false
                val updated = supportedServices + service
                vm.updateSettings(
                    settings.copy(
                        searchServices = updated,
                        searchServiceSelected = updated.lastIndex,
                    )
                )
                nav.navigate(Screen.SettingSearchDetail(service.id.toString()))
            },
        )
    }
}

@Composable
private fun AddSearchProviderDialog(
    choices: List<SearchServiceOptions>,
    onDismiss: () -> Unit,
    onConfirm: (SearchServiceOptions) -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    if (choices.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_page_search_add_provider)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEachIndexed { index, service ->
                    Card(
                        onClick = { selectedIndex = index },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedIndex == index) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AutoAIIcon(name = service.displayName, modifier = Modifier.size(28.dp))
                            Text(service.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choices[selectedIndex]) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SearchProviderCard(
    service: SearchServiceOptions,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AutoAIIcon(
                name = service.displayName,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                SearchAbilityTagLine(options = service)
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = HugeIcons.PencilEdit01,
                    contentDescription = stringResource(R.string.edit),
                )
            }
        }
    }
}

@Composable
fun SearchAbilityTagLine(
    modifier: Modifier = Modifier,
    options: SearchServiceOptions,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (SearchService.isRuntimeSupported(options)) {
            Tag(type = TagType.DEFAULT) {
                Text(stringResource(R.string.search_ability_search))
            }
            if (SearchService.getService(options).scrapingParameters(options) != null) {
                Tag(type = TagType.DEFAULT) {
                    Text(stringResource(R.string.search_ability_scrape))
                }
            }
        }
    }
}

@Composable
private fun CommonOptions(
    settings: com.orchords.orchordsai.data.datastore.Settings,
    onUpdate: (SearchCommonOptions) -> Unit,
) {
    var commonOptions by remember(settings.searchCommonOptions) {
        mutableStateOf(settings.searchCommonOptions)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_page_search_common_options),
                style = MaterialTheme.typography.titleMedium,
            )
            FormItem(
                label = { Text(stringResource(R.string.setting_page_search_result_size)) },
            ) {
                OutlinedNumberInput(
                    value = commonOptions.resultSize,
                    onValueChange = {
                        commonOptions = commonOptions.copy(resultSize = it)
                        onUpdate(commonOptions)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
