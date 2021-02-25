package example.scheduledtask

import functional.orThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import statemachine.applyTransition
import statemachine.dot
import java.net.URI
import java.time.Instant

class ScheduledTaskWorkflowTest {

    @Test
    fun `successfully executes a task and its extensions`() {
        val firstTask = ScheduledTask(Instant.now().plusSeconds(5), URI.create("http://do.this/1"))
        val secondTask = ScheduledTask(Instant.now().plusSeconds(10), URI.create("http://do.this/2"))
        val initialState = NotFound()
        val endState = scheduledTaskWorkflow.applyTransition(initialState, ScheduledTaskWorkflowCreated(firstTask))
            .applyTransition(TaskStarted)
            .applyTransition(TaskExtended(secondTask))
            .applyTransition(TaskStarted)
            .applyTransition(TaskCompleted)
            .orThrow()

        assertEquals(CompleteTask(initialState.id), endState.state)
        assertEquals(
            listOf(
                ScheduledTaskWorkflowCreated(firstTask),
                TaskStarted,
                TaskExtended(secondTask),
                TaskStarted,
                TaskCompleted,
            ), endState.applied
        )
    }

    @Test
    fun `outputs dot graph`() {
        assertEquals(
            """digraph {
  NotFound -> PendingTask [label="ScheduledTaskWorkflowCreated"]
  PendingTask -> ExecutingTask [label="TaskStarted"]
  ExecutingTask -> PendingTask [label="TaskFailed"]
  ExecutingTask -> PendingTask [label="TaskExtended"]
  ExecutingTask -> AbortedTask [label="TaskAborted"]
  PendingTask -> AbortedTask [label="TaskAborted"]
  ExecutingTask -> CompleteTask [label="TaskCompleted"]
}""",
            scheduledTaskWorkflow.dot()
        )
    }
}