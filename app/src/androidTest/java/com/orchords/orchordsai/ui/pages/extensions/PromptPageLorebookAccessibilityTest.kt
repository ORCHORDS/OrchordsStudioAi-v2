package com.orchords.orchordsai.ui.pages.extensions

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation3.runtime.NavKey
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.sonner.rememberToasterState
import com.orchords.orchordsai.R
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.extensions.BuiltInLibrary
import com.orchords.orchordsai.data.extensions.toLorebook
import com.orchords.orchordsai.data.model.Lorebook
import com.orchords.orchordsai.ui.context.LocalNavController
import com.orchords.orchordsai.ui.context.LocalToaster
import com.orchords.orchordsai.ui.context.Navigator
import com.orchords.orchordsai.ui.theme.OrchordsAITheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class PromptPageLorebookAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var settingsStore: SettingsStore
    private lateinit var originalLorebooks: List<Lorebook>

    @Before
    fun setUp() = runBlocking {
        settingsStore = GlobalContext.get().get()
        originalLorebooks = settingsStore.settingsFlow.first { !it.init }.lorebooks
        settingsStore.replaceLorebooks(
            listOf(BuiltInLibrary.catalog.lorebooks.first().toLorebook())
        )
    }

    @After
    fun tearDown() = runBlocking {
        settingsStore.replaceLorebooks(originalLorebooks)
    }

    @Test
    fun lorebookEntryMoveActionIsLocalizedMovesEntryAndKeepsFocus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = PromptVM(settingsStore)
        val lorebookTabLabel = context.getString(R.string.prompt_page_lorebook_tab)
        val editLabel = context.getString(R.string.prompt_page_edit)
        val firstEntry = "Change boundaries"
        val secondEntry = "Regression evidence"
        val moveFirstDownToSecond = context.getString(
            R.string.prompt_page_move_entry_down,
            firstEntry,
            2,
        )
        val moveFirstUpToFirst = context.getString(
            R.string.prompt_page_move_entry_up,
            firstEntry,
            1,
        )
        val moveFirstDownToThird = context.getString(
            R.string.prompt_page_move_entry_down,
            firstEntry,
            3,
        )

        composeRule.setContent {
            val toasterState = rememberToasterState()
            OrchordsAITheme {
                CompositionLocalProvider(
                    LocalNavController provides Navigator(mutableListOf<NavKey>()),
                    LocalToaster provides toasterState,
                ) {
                    PromptPage(vm)
                }
            }
        }

        composeRule.onNodeWithText(lorebookTabLabel).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Software Delivery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(editLabel).onFirst().performClick()

        composeRule.onNodeWithText(firstEntry).assertExists()
        composeRule.onNodeWithText(secondEntry).assertExists()

        val moveDown = composeRule.onNodeWithContentDescription(moveFirstDownToSecond)
        moveDown.assertExists()
        moveDown.performSemanticsAction(SemanticsActions.RequestFocus)
        moveDown.assertIsFocused()
        moveDown.performClick()

        composeRule.onNodeWithContentDescription(moveFirstDownToSecond).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(moveFirstUpToFirst).assertExists()
        composeRule.onNodeWithContentDescription(moveFirstDownToThird)
            .assertExists()
            .assertIsFocused()
    }
}
