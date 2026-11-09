package com.retroboy.arcade

import com.retroboy.arcade.data.AchievementCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {
    @Test fun achievementIdsAreUnique() {
        val ids = AchievementCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everyAchievementHasValidTargetAndPoints() {
        assertTrue(AchievementCatalog.all.all { it.target > 0 && it.points > 0 })
    }
}
