package example.scheduledtask

import commandhandler.Command
import commandhandler.CommandHandler
import functional.*
import functional.Result.Companion.failure
import statemachine.*
import java.time.Clock
import java.time.Duration
import java.time.Instant


sealed class ScheduledTaskWorkflowCommand : Command

data class CreatePendingTask(
    val scheduledTask: ScheduledTask
) : ScheduledTaskWorkflowCommand()

data class StartTask(
    val taskId: StateId
) : ScheduledTaskWorkflowCommand()

data class FailTask(
    val taskId: StateId,
    val reason: String,
    val tryAgainDelay: Duration
) : ScheduledTaskWorkflowCommand()

data class AbortTask(
    val taskId: StateId,
    val reason: String
) : ScheduledTaskWorkflowCommand()

data class RecordTaskSuccessAndExtend(
    val taskId: StateId,
    val nextTask: ScheduledTask
) : ScheduledTaskWorkflowCommand()

data class RecordTaskSuccessAndComplete(
    val taskId: StateId
) : ScheduledTaskWorkflowCommand()

class ScheduledTaskCommandHandler(
    private val fetch: (StateId) -> Result<ErrorCode, ScheduledTaskWorkflowState>,
    private val stateMachine: StateMachine<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>,
    private val clock: Clock
) : CommandHandler<ScheduledTaskWorkflowCommand, ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent> {
    override fun invoke(command: ScheduledTaskWorkflowCommand): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        when (command) {
            is CreatePendingTask -> command.perform()
            is StartTask -> command.perform()
            is FailTask -> command.perform()
            is RecordTaskSuccessAndExtend -> command.perform()
            is RecordTaskSuccessAndComplete -> command.perform()
            is AbortTask -> command.perform()
        }

    private fun CreatePendingTask.perform(): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        stateMachine.applyTransition(NotFound(), ScheduledTaskWorkflowCreated(this.scheduledTask))

    private fun StartTask.perform(): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        fetch(this.taskId)
            .flatMap { task ->
                when (task) {
                    is PendingTask ->
                        stateMachine.applyTransitionWithSideEffect(task, { pending: PendingTask ->
                            val now = clock.instant()
                            if (pending.scheduledTask.invokeAfter.isBefore(now)) {
                                TaskStarted.asSuccess()
                            } else {
                                CannotExecuteTaskBeforeInvokeAfterDate(taskId, pending.scheduledTask, now).asFailure()
                            }
                        })
                    else -> failure(ProcessInWrongState(this, task))
                }
            }

    private fun FailTask.perform(): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        fetch(this.taskId).flatMap {
            stateMachine.applyTransition(it, TaskFailed(this.reason, this.tryAgainDelay))
        }

    private fun RecordTaskSuccessAndExtend.perform(): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        fetch(this.taskId).flatMap {
            stateMachine.applyTransition(it, TaskExtended(this.nextTask))
        }

    private fun RecordTaskSuccessAndComplete.perform(): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        fetch(this.taskId).flatMap {
            stateMachine.applyTransition(it, TaskCompleted)
        }

    private fun AbortTask.perform(): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> =
        fetch(this.taskId).flatMap {
            stateMachine.applyTransition(it, TaskAborted(this.reason))
        }

}


sealed class ScheduledTaskWorkflowError : ErrorCode

data class ScheduledTaskWorkflowNotFound(val taskId: StateId) : ScheduledTaskWorkflowError()
data class CannotExecuteTaskBeforeInvokeAfterDate(
    val taskId: StateId,
    val scheduledTask: ScheduledTask,
    val now: Instant
) : ScheduledTaskWorkflowError()

data class ProcessInWrongState(val command: ScheduledTaskWorkflowCommand, val state: ScheduledTaskWorkflowState) :
    ScheduledTaskWorkflowError()
