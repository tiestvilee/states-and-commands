package example.scheduledtask

import commandhandler.InMemoryPersistence
import commandhandler.PersistentCommandHandler
import commandhandler.UmlCommandHandler
import commandhandler.UmlRenderer
import functional.Result
import functional.map
import functional.orThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class TickingClock : Clock() {
    override fun getZone() = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId?): Clock {
        TODO("Not yet implemented")
    }

    val currentTick = AtomicLong()
    override fun instant() = Instant.ofEpochMilli(currentTick.get())

    fun tick(by: Duration) {
        currentTick.addAndGet(by.toMillis())
    }

}

class ScheduledTaskWorkflowHubTest {
    @Test
    fun `can call a bunch of hub endpoints`() {
        val datasource = InMemoryPersistence<ScheduledTaskWorkflowEvent>()
        val tickingClock = TickingClock()

        val commandHandler = ScheduledTaskCommandHandler(
            { id ->
                datasource.fetchTransitionsFor(id)
                    .map { transitions ->
                        scheduledTaskWorkflow.foldOverTransitionsIntoState(NotFound(id), transitions)
                    }
            },
            scheduledTaskWorkflow,
            tickingClock
        )
        val persistentHandler = PersistentCommandHandler(
            commandHandler,
            datasource::save
        )
        val umlRenderer = UmlRenderer(object {}.javaClass.enclosingMethod.name)
        val dottyHandler = UmlCommandHandler(persistentHandler, umlRenderer)

        val handler = dottyHandler

        val firstTask = ScheduledTask(tickingClock.instant().plusSeconds(-5), URI.create("http://do.this/1"))
        val secondTask = ScheduledTask(tickingClock.instant().plusSeconds(5), URI.create("http://do.this/2"))
        var returnSecondTask = true

        val hub = UmlScheduledTaskWorflowHub(
            ScheduledTaskWorkflowHubImpl(
                handler,
                PendingJobsProjection(tickingClock)
            ) {
                Result.Success(if (returnSecondTask) secondTask else null)
            },
            umlRenderer
        )

        val result = hub.createTask(firstTask).orThrow()
        returnSecondTask = true
        hub.runPendingTasks()
        returnSecondTask = false
        tickingClock.tick(Duration.ofSeconds(6))
        hub.runPendingTasks()

        val state = datasource.fetchTransitionsFor(result.state.id)
            .map { transitions ->
                scheduledTaskWorkflow.foldOverTransitionsIntoState(NotFound(result.state.id), transitions)
            }.orThrow()

        File("src/test/kotlin/example/scheduledtask/hub.puml")
            .writeText(umlRenderer.toUml())

        assertEquals(CompleteTask(result.state.id), state)
    }
}

