package example.scheduledtask

import commandhandler.InMemoryPersistence
import commandhandler.PersistentCommandHandler
import functional.map
import functional.orThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ScheduledTaskWorkflowCommandHandlerTest {
    @Test
    fun `can apply a bunch of commands`() {
        val datasource = InMemoryPersistence<ScheduledTaskWorkflowEvent>()
        val handler = ScheduledTaskCommandHandler(
            { id ->
                datasource.fetchTransitionsFor(id)
                    .map { transitions ->
                        scheduledTaskWorkflow.foldOverTransitionsIntoState(NotFound(id), transitions)
                    }
            },
            scheduledTaskWorkflow,
            Clock.fixed(Instant.now(), ZoneOffset.UTC)
        )
        val persistentHandler = PersistentCommandHandler(
            handler,
            datasource::save
        )

        val firstTask = ScheduledTask(Instant.now().plusSeconds(-5), URI.create("http://do.this/1"))
        val secondTask = ScheduledTask(Instant.now().plusSeconds(-1), URI.create("http://do.this/2"))

        val entityId = persistentHandler.invoke(CreatePendingTask(firstTask)).orThrow().state.id
        persistentHandler.invoke(StartTask(entityId)).orThrow()
        persistentHandler.invoke(RecordTaskSuccessAndExtend(entityId, secondTask)).orThrow()

        val state = datasource.fetchTransitionsFor(entityId)
            .map { transitions ->
                scheduledTaskWorkflow.foldOverTransitionsIntoState(NotFound(entityId), transitions)
            }.orThrow()

        assertEquals(PendingTask(entityId, secondTask), state)
    }
}

