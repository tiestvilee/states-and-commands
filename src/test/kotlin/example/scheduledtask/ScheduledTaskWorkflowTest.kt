package example.scheduledtask

import functional.orThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import statemachine.applyTransition
import statemachine.puml
import java.io.File
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
        val uml = scheduledTaskWorkflow.puml()
        File("src/test/kotlin/example/scheduledtask/states.puml")
            .writeText(uml)

        assertEquals(
            """@startuml
  [*] --> NotFound
  NotFound --> PendingTask : ScheduledTaskWorkflowCreated
  PendingTask --> ExecutingTask : TaskStarted
  ExecutingTask --> PendingTask : TaskFailed
  ExecutingTask --> PendingTask : TaskExtended
  ExecutingTask --> AbortedTask : TaskAborted
  PendingTask --> AbortedTask : TaskAborted
  ExecutingTask --> CompleteTask : TaskCompleted
  AbortedTask --> [*]
  CompleteTask --> [*]
@enduml""",
            uml
        )

    }
}