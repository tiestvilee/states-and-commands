//package example.scheduledtask
//
//import commandhandler.InMemoryPersistence
//import commandhandler.PersistentCommandHandler
//import functional.map
//import functional.orThrow
//import org.junit.Assert.assertEquals
//import org.junit.Test
//import statemachine.StateId
//import java.net.URI
//import java.time.Clock
//import java.time.Instant
//import java.time.ZoneOffset
//
//class ScheduledTaskWorkflowCommandHandlerTest {
//    @Test
//    fun `can apply a bunch of commands`() {
//        val datasource = InMemoryPersistence()
//        val handler = ScheduledTaskCommandHandler(
//            datasource::foldOverTransitions,
//            Clock.fixed(Instant.now(), ZoneOffset.UTC)
//        )
//        val persistentHandler = PersistentCommandHandler(
//            handler,
//            datasource::save
//        )
//
//        val firstTask = ScheduledTask(Instant.now().plusSeconds(-5), URI.create("http://do.this/1"))
//        val secondTask = ScheduledTask(Instant.now().plusSeconds(-1), URI.create("http://do.this/2"))
//
//        val entityId = persistentHandler.invoke(CreatePendingTask(firstTask)).orThrow().new.id
//        persistentHandler.invoke(StartTask(entityId)).orThrow()
//        persistentHandler.invoke(RecordTaskSuccessAndExtend(entityId, secondTask)).orThrow()
//
//        val state = datasource.foldOverTransitions(entityId).orThrow()
//
//        assertEquals(PendingTask(entityId, secondTask), state)
//    }
//}
//
//private fun InMemoryPersistence.foldOverTransitions(id: StateId) =
//    this.fetchTransitionsFor(id)
//        .map { it.foldOverTransitionsIntoState(NotFound(id)) }
