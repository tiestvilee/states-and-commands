package example.scheduledtask

import statemachine.State
import statemachine.StateId
import statemachine.StateMachine
import statemachine.Transition
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*

/* domain objects */
data class ScheduledTask(val invokeAfter: Instant, val action: URI)

/* Workflow */

val scheduledTaskWorkflow = StateMachine<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>()
    .defineStateTransition { initialState: NotFound, created: ScheduledTaskWorkflowCreated ->
        PendingTask(initialState.id, created.scheduledTask)
    }
    .defineStateTransition { pending: PendingTask, _: TaskStarted ->
        ExecutingTask(pending.id, pending.scheduledTask)
    }
    .defineStateTransition { executing: ExecutingTask, failed: TaskFailed ->
        PendingTask(executing.id, executing.originalTask.let {
            it.copy(invokeAfter = it.invokeAfter.plusMillis(failed.tryAgainDelay.toMillis()))
        })
    }
    .defineStateTransition { executing: ExecutingTask, extended: TaskExtended ->
        PendingTask(executing.id, extended.scheduledTask)
    }
    .defineStateTransition { executing: ExecutingTask, _: TaskAborted ->
        AbortedTask(executing.id)
    }
    .defineStateTransition { pending: PendingTask, _: TaskAborted ->
        AbortedTask(pending.id)
    }
    .defineStateTransition { executing: ExecutingTask, _: TaskCompleted ->
        CompleteTask(executing.id)
    }

/* States */
sealed class ScheduledTaskWorkflowState : State {
    abstract val id: StateId
}

data class NotFound(override val id: StateId = StateId(UUID.randomUUID())) : ScheduledTaskWorkflowState()

data class PendingTask(override val id: StateId, val scheduledTask: ScheduledTask) : ScheduledTaskWorkflowState()
data class ExecutingTask(
    override val id: StateId,
    val originalTask: ScheduledTask
) : ScheduledTaskWorkflowState()

data class CompleteTask(override val id: StateId) : ScheduledTaskWorkflowState()
data class AbortedTask(override val id: StateId) : ScheduledTaskWorkflowState()

/* Transitions */

sealed class ScheduledTaskWorkflowEvent : Transition

// I removed the id from these events because they weren't used... except in the persistence.
data class ScheduledTaskWorkflowCreated(val scheduledTask: ScheduledTask) :
    ScheduledTaskWorkflowEvent()

object TaskStarted : ScheduledTaskWorkflowEvent()
data class TaskExtended(val scheduledTask: ScheduledTask) : ScheduledTaskWorkflowEvent()
data class TaskFailed(val reason: String, val tryAgainDelay: Duration) :
    ScheduledTaskWorkflowEvent()

object TaskCompleted : ScheduledTaskWorkflowEvent()
data class TaskAborted(val reason: String) : ScheduledTaskWorkflowEvent()