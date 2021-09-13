package example.scheduledtask

import commandhandler.CommandHandler
import functional.ErrorCode
import functional.Result
import functional.flatMapFailure
import functional.map
import statemachine.Application
import statemachine.StateId
import java.net.URI
import java.time.Clock
import java.time.Duration

data class PendingJob(val id: StateId, val task: ScheduledTask)
class PendingJobsProjection(val clock: Clock) {
    val pendingJobs = mutableListOf<PendingJob>()
    fun update(state: ScheduledTaskWorkflowState) {
        when (state) {
            is AbortedTask -> pendingJobs.removeIf { it.id == state.id }
            is CompleteTask -> pendingJobs.removeIf { it.id == state.id }
            is ExecutingTask -> pendingJobs.removeIf { it.id == state.id }
            is NotFound -> Unit // nothing
            is PendingTask -> pendingJobs.add(PendingJob(state.id, state.scheduledTask))
        }
    }

    fun map(f: (PendingJob) -> Result<ErrorCode, Unit>) {
        while (true) {
            val shouldExecute = pendingJobs
                .filter { clock.instant().isAfter(it.task.invokeAfter) }
            if (shouldExecute.isEmpty()) {
                break
            } else {
                f(shouldExecute.first())
            }
        }
    }
}

data class JobFailure(val message: String) : ErrorCode

class ScheduledTaskWorkflowHub(
    val originalCommandHandler: CommandHandler<ScheduledTaskWorkflowCommand, ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>,
    val pendingJobs: PendingJobsProjection,
    val jobHandler: (URI) -> Result<ErrorCode, ScheduledTask?>,
) {
    val commandHandler =
        object : CommandHandler<ScheduledTaskWorkflowCommand, ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent> {
            override fun invoke(command: ScheduledTaskWorkflowCommand): Result<ErrorCode, Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent>> {
                return originalCommandHandler.invoke(command).map { application ->
                    pendingJobs.update(application.state)
                    application
                }
            }
        }

    fun createTask(task: ScheduledTask) =
        commandHandler.invoke(CreatePendingTask(task))

    fun runPendingTasks() {
        pendingJobs.map { job: PendingJob ->
            commandHandler.invoke(StartTask(job.id))
                .map { application: Application<ScheduledTaskWorkflowState, ScheduledTaskWorkflowEvent> ->
                    // we should use application here?
                    jobHandler(job.task.action).map {
                        when (it) {
                            is ScheduledTask -> commandHandler.invoke(RecordTaskSuccessAndExtend(job.id, it))
                            else -> commandHandler.invoke(RecordTaskSuccessAndComplete(job.id))
                        }
                    }.flatMapFailure {
                        commandHandler.invoke(FailTask(job.id, it.toString(), Duration.ofSeconds(30)))
                    }
                }
        }
    }
}