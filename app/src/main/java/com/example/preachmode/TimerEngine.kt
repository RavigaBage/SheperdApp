package com.example.preachmode

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class TimerState(
    val elapsed: Long = 0L,
    val remaining: Long = 0L,
    val isUp: Boolean = false,
    val isPulsing: Boolean = false
)

object TimerEngine {
    fun startTimer(totalMillis: Long): Flow<TimerState> = flow {
        val milestoneMinutes = setOf(30, 20, 5, 2, 1)
        var remaining = totalMillis
        var pulseEndTime = 0L
        
        while (remaining > 0) {
            val elapsed = totalMillis - remaining
            
            // Determine if we are at a milestone minute crossing (within 1 second of it)
            val currentRemainingMinutes = (remaining + 500) / 1000 / 60
            val currentRemainingSeconds = ((remaining + 500) / 1000) % 60
            
            val isExactlyMilestone = currentRemainingSeconds == 0L && milestoneMinutes.contains(currentRemainingMinutes.toInt())
            if (isExactlyMilestone) {
                pulseEndTime = System.currentTimeMillis() + 10_000L
            }
            
            val isPulsing = System.currentTimeMillis() < pulseEndTime
            
            emit(
                TimerState(
                    elapsed = elapsed,
                    remaining = remaining,
                    isUp = false,
                    isPulsing = isPulsing
                )
            )
            delay(1000)
            remaining -= 1000
        }
        
        // Time's up! Continuous pulse until dismissed
        emit(
            TimerState(
                elapsed = totalMillis,
                remaining = 0L,
                isUp = true,
                isPulsing = true
            )
        )
    }
}
