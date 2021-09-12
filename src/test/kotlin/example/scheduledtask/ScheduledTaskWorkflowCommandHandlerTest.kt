package example.scheduledtask

import commandhandler.InMemoryPersistence
import commandhandler.PersistentCommandHandler
import commandhandler.UmlCommandHandler
import functional.map
import functional.orThrow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ScheduledTaskWorkflowCommandHandlerTest {
    @Test
    fun `can apply a bunch of commands`() {
        val datasource = InMemoryPersistence<ScheduledTaskWorkflowEvent>()
        val commandHandler = ScheduledTaskCommandHandler(
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
            commandHandler,
            datasource::save
        )
        val dottyHandler = UmlCommandHandler(persistentHandler, object {}.javaClass.enclosingMethod.name)

        val handler = dottyHandler

        val firstTask = ScheduledTask(Instant.now().plusSeconds(-5), URI.create("http://do.this/1"))
        val secondTask = ScheduledTask(Instant.now().plusSeconds(-1), URI.create("http://do.this/2"))

        val entityId = handler.invoke(CreatePendingTask(firstTask)).orThrow().state.id
        handler.invoke(StartTask(entityId)).orThrow()
        handler.invoke(RecordTaskSuccessAndExtend(entityId, secondTask)).orThrow()
        handler.invoke(StartTask(entityId)).orThrow()
        handler.invoke(RecordTaskSuccessAndComplete(entityId)).orThrow()

        val state = datasource.fetchTransitionsFor(entityId)
            .map { transitions ->
                scheduledTaskWorkflow.foldOverTransitionsIntoState(NotFound(entityId), transitions)
            }.orThrow()

        assertEquals(CompleteTask(entityId), state)

        File("src/test/kotlin/example/scheduledtask/command.puml")
            .writeText(dottyHandler.uml())
    }
}

