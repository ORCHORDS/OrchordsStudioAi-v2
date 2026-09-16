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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.orchords.orchordsai.data.ai.tools.normalizeOrchordsSearchOptions
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
import me.orchid.hugeicons.stroke.PencilEdit01
import org.koin.androidx.compose.koinViewModel

/**
 * First-party search settings.
 *
 * Bing and the old provider picker are intentionally not presented. Legacy
 * serialized provider records stay readable elsewhere for migration safety,
 * but this surface normalizes the active configuration to one repaired
 * Orchords Search record and one selected index.
 */
@Composable
fun SettingSearchPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val nav = LocalNavController.current
    val storedOrchordsSearch = remember(settings.searchServices) {
        settings.searchServices
            .filterIsInstance<SearchServiceOptions.OrchordsAIOptions>()
            .firstOrNull()
            ?: SearchServiceOptions.OrchordsAIOptions()
    }
    val orchordsSearch = remember(storedOrchordsSearch) {
        normalizeOrchordsSearchOptions(storedOrchordsSearch)
    }
    val needsNormalization =
        settings.searchServices.size != 1 ||
            settings.searchServices.firstOrNull() !is SearchServiceOptions.OrchordsAIOptions ||
            settings.searchServiceSelected != 0 ||
            storedOrchordsSearch != orchordsSearch

    LaunchedEffect(orchordsSearch, needsNormalization) {
        if (needsNormalization) {
            vm.updateSettings(
                settings.copy(
                    searchServices = listOf(orchordsSearch),
                    searchServiceSelected = 0,
                )
            )
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_search_title)) },
                navigationIcon = { BackButton() },
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
            item("orchords_search") {
                SearchProviderCard(
                    service = orchordsSearch,
                    onEdit = {
                        nav.navigate(Screen.SettingSearchDetail(orchordsSearch.id.toString()))
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
}

@Composable
private fun SearchProviderCard(
    service: SearchServiceOptions.OrchordsAIOptions,
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
