package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.MainCoroutineExtension
import com.theveloper.pixelplay.data.model.PlaybackQueueItemSnapshot
import com.theveloper.pixelplay.data.model.PlaybackQueueSnapshot
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaybackStateHolderTest {

    private val dualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val castStateHolder: CastStateHolder = mockk(relaxed = true)
    private val queueStateHolder: QueueStateHolder = mockk(relaxed = true)
    private val listeningStatsTracker: ListeningStatsTracker = mockk(relaxed = true)

    private fun createHolder() = PlaybackStateHolder(
        dualPlayerEngine = dualPlayerEngine,
        userPreferencesRepository = userPreferencesRepository,
        castStateHolder = castStateHolder,
        queueStateHolder = queueStateHolder,
        listeningStatsTracker = listeningStatsTracker
    )

    @Test
    fun `paused override does not bleed into later occurrence with same media id`() {
        val holder = createHolder()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.rememberPausedPositionOverride("duplicate-song", 91_000L)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `cold start snapshot only applies to the first matching occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns PlaybackQueueSnapshot(
            items = listOf(
                PlaybackQueueItemSnapshot(
                    mediaId = "duplicate-song",
                    uri = "file:///music/duplicate-song.mp3"
                )
            ),
            currentMediaId = "duplicate-song",
            currentIndex = 0,
            currentPositionMs = 48_000L
        )

        val holder = createHolder()
        holder.initialize(this)
        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(48_000L, holder.currentPosition.value)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }
}
