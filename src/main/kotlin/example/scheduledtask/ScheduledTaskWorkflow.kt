package example.scheduledtask

import statemachine.State
import statemachine.StateId
import statemachine.Transition
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

/* domain objects */
data class ScheduledTask(val invokeAfter: Instant, val action: URI)

/* Workflow */
sealed class ScheduledTaskWorkflow : State() {
    companion object {

        val stateTransitionTable =
            mutableMapOf<Pair<KClass<out ScheduledTaskWorkflow>, KClass<out ScheduledTaskWorkflowEvent>>, (State, Transition) -> State>()

        @Suppress("UNCHECKED_CAST")
        inline fun <reified S : ScheduledTaskWorkflow, reified T : ScheduledTaskWorkflowEvent, S2 : ScheduledTaskWorkflow> defineStateTransition(
            noinline transform: (S, T) -> S2
        ) {
            stateTransitionTable[Pair(S::class, T::class)] = transform as (State, Transition) -> State
        }

        init {
            defineStateTransition { initialState: NotFound, created: ScheduledTaskWorkflowCreated ->
                PendingTask(initialState.id, created.scheduledTask)
            }
            defineStateTransition { pending: PendingTask, _: TaskStarted ->
                ExecutingTask(pending.id, pending.scheduledTask)
            }
            defineStateTransition { executing: ExecutingTask, failed: TaskFailed ->
                PendingTask(executing.id, executing.originalTask.let {
                    it.copy(invokeAfter = it.invokeAfter.plusMillis(failed.tryAgainDelay.toMillis()))
                })
            }
            defineStateTransition { executing: ExecutingTask, extended: TaskExtended ->
                PendingTask(executing.id, extended.scheduledTask)
            }
            defineStateTransition { executing: ExecutingTask, _: TaskAborted ->
                AbortedTask(executing.id)
            }
            defineStateTransition { pending: PendingTask, _: TaskAborted ->
                AbortedTask(pending.id)
            }
            defineStateTransition { executing: ExecutingTask, _: TaskCompleted ->
                CompleteTask(executing.id)
            }
        }
    }

    override fun getTransitionFunction(transitionClass: KClass<out Transition>) =
        stateTransitionTable[Pair(this::class, transitionClass)]
}

/* States */

data class NotFound(override val id: StateId = StateId(UUID.randomUUID())) : ScheduledTaskWorkflow()

data class PendingTask(override val id: StateId, val scheduledTask: ScheduledTask) : ScheduledTaskWorkflow()
data class ExecutingTask(
    override val id: StateId,
    val originalTask: ScheduledTask
) : ScheduledTaskWorkflow()

data class CompleteTask(override val id: StateId) : ScheduledTaskWorkflow()
data class AbortedTask(override val id: StateId) : ScheduledTaskWorkflow()

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