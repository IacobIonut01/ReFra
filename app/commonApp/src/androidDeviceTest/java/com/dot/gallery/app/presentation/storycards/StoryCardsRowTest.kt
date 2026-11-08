package com.dot.gallery.app.presentation.storycards

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.app.presentation.storycards.components.StoryCardsRow
import com.dot.gallery.app.presentation.storycards.components.StoryCardsRowTag
import com.dot.gallery.app.presentation.storycards.components.StoryCardsLoadingRowTag
import com.dot.gallery.app.presentation.storycards.components.storyCardTitleTag
import com.dot.gallery.app.presentation.storycards.components.storyCardTypeTag
import com.dot.gallery.app.presentation.storycards.components.storyCardScrimTag
import com.dot.gallery.app.presentation.storycards.components.storyCardTag
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.story_type_albums
import com.dot.gallery.core.util.getString
import androidx.compose.ui.unit.dp



@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class StoryCardsRowTest {

    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    @Test
    fun singleCardUsesTheAvailablePhoneWidthWithoutMasking() {
        val card = StoryCard(id = 1L, type = StoryCardType.MEMORIES, title = "On this day")
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = listOf(card), onCardClick = {})
                }
            }
        }

        val bounds = rule.onNodeWithTag(storyCardTag(1L)).getUnclippedBoundsInRoot()
        (bounds.right - bounds.left).assertIsEqualTo(368.dp, "single highlight width")
        rule.onNodeWithTag(storyCardTitleTag(1L), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun twoCardsAreEqualAndFullyVisibleInsideBothGutters() {
        val cards = listOf(
            StoryCard(id = 1L, type = StoryCardType.ALBUMS, title = "Camera"),
            StoryCard(id = 2L, type = StoryCardType.LOCATIONS, title = "At the coast"),
        )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = cards, onCardClick = {})
                }
            }
        }

        val first = rule.onNodeWithTag(storyCardTag(1L)).getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag(storyCardTag(2L)).getUnclippedBoundsInRoot()
        (first.right - first.left).assertIsEqualTo(180.dp, "first highlight width")
        (second.right - second.left).assertIsEqualTo(180.dp, "second highlight width")
        first.left.assertIsEqualTo(16.dp, "leading gutter")
        second.right.assertIsEqualTo(384.dp, "trailing content edge")
    }

    @Test
    fun scrimCoversTheWholeCardRatherThanOnlyTheCaption() {
        val cards = listOf("Camera", "Travel", "Favorites").mapIndexed { index, title ->
            StoryCard(id = index.toLong(), type = StoryCardType.ALBUMS, title = title)
        }
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = cards, onCardClick = {})
                }
            }
        }

        val card = rule.onNodeWithTag(storyCardTag(1L)).getUnclippedBoundsInRoot()
        val scrim = rule.onNodeWithTag(storyCardScrimTag(1L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        scrim.left.assertIsEqualTo(card.left, "scrim left edge")
        scrim.right.assertIsEqualTo(card.right, "scrim right edge")
    }

    @Test
    fun memoryAndLocationCardsShowTheirTypeBadges() {
        val cards = listOf(
            StoryCard(id = 1L, type = StoryCardType.MEMORIES, title = "On this day"),
            StoryCard(id = 2L, type = StoryCardType.LOCATIONS, title = "At the coast"),
        )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = cards, onCardClick = {})
                }
            }
        }

        rule.onNodeWithTag(storyCardTypeTag(1L), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(storyCardTypeTag(2L), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fastFlingCanAdvanceMoreThanOneCard() {
        val cards = (0L..11L).map { id ->
            StoryCard(id = id, type = StoryCardType.ALBUMS, title = "Album $id")
        }
        lateinit var carouselState: CarouselState
        rule.setContent {
            carouselState = rememberCarouselState { cards.size }
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = cards, onCardClick = {}, carouselState = carouselState)
                }
            }
        }

        rule.onNodeWithTag(StoryCardsRowTag).performTouchInput {
            swipeWithVelocity(
                start = percentOffset(0.7f, 0.5f),
                end = percentOffset(0.5f, 0.5f),
                endVelocity = width * 5f,
                durationMillis = 80,
            )
        }
        rule.runOnIdle {
            assertTrue("A quick fling should browse multiple highlights", carouselState.currentItem > 1)
        }
    }

    @Test
    fun loadingAndReadyCardsHaveTheSameHeight() {
        var cards: List<StoryCard>? by mutableStateOf(null)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = cards, onCardClick = {})
                }
            }
        }

        rule.onNodeWithTag(StoryCardsLoadingRowTag).assertHeightIsEqualTo(220.dp)
        rule.runOnIdle {
            cards = listOf(StoryCard(id = 1L, type = StoryCardType.ALBUMS, title = "Nature"))
        }
        rule.onNodeWithTag(StoryCardsLoadingRowTag).assertDoesNotExist()
        rule.onNodeWithTag(StoryCardsRowTag).assertIsDisplayed().assertHeightIsEqualTo(220.dp)
    }

    @Test
    fun compressedMiddleCardDoesNotClipItsCaption() {
        val cards = listOf("Space", "Travel", "Nature").mapIndexed { index, title ->
            StoryCard(id = index.toLong(), type = StoryCardType.ALBUMS, title = title)
        }
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StoryCardsRow(cards = cards, onCardClick = {})
                }
            }
        }

        val title = rule.onNodeWithTag(storyCardTitleTag(1L), useUnmergedTree = true)
        title.assertIsDisplayed()
        val visible = title.getBoundsInRoot()
        val measured = title.getUnclippedBoundsInRoot()
        (visible.right - visible.left).assertIsEqualTo(
            measured.right - measured.left,
            "visible width of the compressed card caption",
        )
    }

    @Test
    fun emptyCardsDoNotReserveCarouselSpace() {
        rule.setContent {
            MaterialTheme {
                StoryCardsRow(cards = emptyList(), onCardClick = {})
            }
        }

        rule.onNodeWithTag(StoryCardsRowTag).assertDoesNotExist()
    }

    @Test
    fun card_exposesLocalizedDescriptionAndDeliversClick() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val card = StoryCard(
            id = 42L,
            type = StoryCardType.ALBUMS,
            title = "Camera",
            subtitle = "3 items",
        )
        var clickedCardId: Long? = null

        rule.setContent {
            MaterialTheme {
                StoryCardsRow(cards = listOf(card), onCardClick = { clickedCardId = it.id })
            }
        }

        rule.onNodeWithTag(storyCardTag(card.id))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertContentDescriptionEquals(
                listOf(
                    context.getString(Res.string.story_type_albums),
                    card.title,
                    card.subtitle,
                ).joinToString(". ")
            )
            .performClick()

        rule.runOnIdle { assertEquals(card.id, clickedCardId) }
    }
}
