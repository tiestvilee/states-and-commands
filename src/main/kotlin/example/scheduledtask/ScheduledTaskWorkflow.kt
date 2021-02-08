package example.scheduledtask

import statemachine.State
import statemachine.Transition
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

/* domain objects */
data class TaskId(val id: UUID)
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
            defineStateTransition { _: InitialStateST, created: ScheduledTaskWorkflowCreated ->
                PendingTask(InitialStateST.taskId, created.scheduledTask)
            }
            defineStateTransition { pending: PendingTask, _: TaskStarted ->
                ExecutingTask(pending.taskId, pending.scheduledTask)
            }
            defineStateTransition { executing: ExecutingTask, failed: TaskFailed ->
                PendingTask(executing.taskId, executing.originalTask.let {
                    it.copy(invokeAfter = it.invokeAfter.plusMillis(failed.tryAgainDelay.toMillis()))
                })
            }
            defineStateTransition { executing: ExecutingTask, extended: TaskExtended ->
                PendingTask(executing.taskId, extended.scheduledTask)
            }
            defineStateTransition { executing: ExecutingTask, _: TaskAborted ->
                AbortedTask(executing.taskId)
            }
            defineStateTransition { pending: PendingTask, _: TaskAborted ->
                AbortedTask(pending.taskId)
            }
            defineStateTransition { executing: ExecutingTask, _: TaskCompleted ->
                CompleteTask(executing.taskId)
            }
        }
    }

    abstract val taskId: TaskId

    override fun getTransitionFunction(transitionClass: KClass<out Transition>) =
        stateTransitionTable[Pair(this::class, transitionClass)]
}

/* States */

object InitialStateST : ScheduledTaskWorkflow() {
    override val taskId: TaskId = TaskId(UUID.randomUUID())
}

data class PendingTask(override val taskId: TaskId, val scheduledTask: ScheduledTask) : ScheduledTaskWorkflow()
data class ExecutingTask(
    override val taskId: TaskId,
    val originalTask: ScheduledTask
) : ScheduledTaskWorkflow()

data class CompleteTask(override val taskId: TaskId) : ScheduledTaskWorkflow()
data class AbortedTask(override val taskId: TaskId) : ScheduledTaskWorkflow()

/* Transitions */

sealed class ScheduledTaskWorkflowEvent : Transition

// I removed the taskId from these events because they weren't used... except in the persistence.
data class ScheduledTaskWorkflowCreated(val scheduledTask: ScheduledTask) :
    ScheduledTaskWorkflowEvent()

object TaskStarted : ScheduledTaskWorkflowEvent()
data class TaskExtended(val scheduledTask: ScheduledTask) : ScheduledTaskWorkflowEvent()
data class TaskFailed(val reason: String, val tryAgainDelay: Duration) :
    ScheduledTaskWorkflowEvent()

object TaskCompleted : ScheduledTaskWorkflowEvent()
data class TaskAborted(val reason: String) : ScheduledTaskWorkflowEvent()