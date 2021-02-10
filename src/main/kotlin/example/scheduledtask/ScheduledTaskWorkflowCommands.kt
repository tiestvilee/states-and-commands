package example.scheduledtask

import commandhandler.Command
import commandhandler.CommandHandler
import functional.*
import statemachine.Application
import statemachine.State
import statemachine.StateId
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
    private val fetch: (StateId) -> Result<ErrorCode, State>,
    private val clock: Clock
) : CommandHandler<ScheduledTaskWorkflowCommand> {
    override fun invoke(command: ScheduledTaskWorkflowCommand): Result<ErrorCode, Application> =
        when (command) {
            is CreatePendingTask -> command.perform()
            is StartTask -> command.perform()
            is FailTask -> command.perform()
            is RecordTaskSuccessAndExtend -> command.perform()
            is RecordTaskSuccessAndComplete -> command.perform()
            is AbortTask -> command.perform()
        }

    private fun CreatePendingTask.perform(): Result<ErrorCode, Application> =
        NotFound().applyTransition(ScheduledTaskWorkflowCreated(this.scheduledTask))

    private fun StartTask.perform(): Result<ErrorCode, Application> = fetch(this.taskId)
        .flatMap { task ->
            task.applyTransition { pending: PendingTask ->
                val now = clock.instant()
                if (pending.scheduledTask.invokeAfter.isBefore(now)) {
                    TaskStarted.asSuccess()
                } else {
                    CannotExecuteTaskBeforeInvokeAfterDate(taskId, pending.scheduledTask, now).asFailure()
                }
            }
        }

    private fun FailTask.perform(): Result<ErrorCode, Application> = fetch(this.taskId).flatMap {
        it.applyTransition(TaskFailed(this.reason, this.tryAgainDelay))
    }

    private fun RecordTaskSuccessAndExtend.perform(): Result<ErrorCode, Application> = fetch(this.taskId).flatMap {
        it.applyTransition(TaskExtended(this.nextTask))
    }

    private fun RecordTaskSuccessAndComplete.perform(): Result<ErrorCode, Application> = fetch(this.taskId).flatMap {
        it.applyTransition(TaskCompleted)
    }

    private fun AbortTask.perform(): Result<ErrorCode, Application> = fetch(this.taskId).flatMap {
        it.applyTransition(TaskAborted(this.reason))
    }

}


sealed class ScheduledTaskWorkflowError : ErrorCode

data class ScheduledTaskWorkflowNotFound(val taskId: StateId) : ScheduledTaskWorkflowError()
data class CannotExecuteTaskBeforeInvokeAfterDate(
    val taskId: StateId,
    val scheduledTask: ScheduledTask,
    val now: Instant
) : ScheduledTaskWorkflowError()

data class ProcessInWrongState(val command: ScheduledTaskWorkflowCommand, val state: ScheduledTaskWorkflow) :
    ScheduledTaskWorkflowError()
