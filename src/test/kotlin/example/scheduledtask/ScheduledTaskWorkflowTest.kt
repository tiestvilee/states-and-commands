package example.scheduledtask

import functional.orThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import statemachine.applyTransition
import java.net.URI
import java.time.Instant

class ScheduledTaskWorkflowTest {

    @Test
    fun `successfully executes a task and its extensions`() {
        val firstTask = ScheduledTask(Instant.now().plusSeconds(5), URI.create("http://do.this/1"))
        val secondTask = ScheduledTask(Instant.now().plusSeconds(10), URI.create("http://do.this/2"))
        val initialState = InitialStateST
        val endState = initialState
            .applyTransition(ScheduledTaskWorkflowCreated(firstTask))
            .applyTransition(TaskStarted)
            .applyTransition(TaskExtended(secondTask))
            .applyTransition(TaskStarted)
            .applyTransition(TaskCompleted)
            .orThrow()

        assertEquals(CompleteTask(initialState.taskId), endState.new)
        assertEquals(
            listOf(
                ScheduledTaskWorkflowCreated(firstTask),
                TaskStarted,
                TaskExtended(secondTask),
                TaskStarted,
                TaskCompleted,
            ), endState.flattenTransitions()
        )
    }
}