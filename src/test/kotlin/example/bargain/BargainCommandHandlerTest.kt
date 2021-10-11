package example.bargain

import commandhandler.InMemoryPersistence
import commandhandler.PersistentCommandHandler
import commandhandler.UmlCommandHandler
import commandhandler.UmlRenderer
import functional.Result
import functional.map
import functional.orThrow
import org.junit.Test
import java.io.File

class BargainCommandHandlerTest {
    @Test
    fun `can apply a bunch of commands`() {
        val umlRenderer = UmlRenderer(object {}.javaClass.enclosingMethod.name)

        val datasource = InMemoryPersistence<BargainEvent>()
        val commandHandler = BargainCommandHandler(
            { ad, user ->
                val allEntites = datasource.fetchTransitionsForAllStates { id, transitions ->
                    bargainStateMachine.foldOverTransitionsIntoState(NotFound(id), transitions)
                }
                allEntites.find {
                    when (it) {
                        is WaitingForAcceptance -> true
                        else -> false
                    }
                }?.let { Result.Success(it) }
                    ?: Result.Failure(BargainNotFound(user, ad))
            },
            { id ->
                datasource.fetchTransitionsFor(id)
                    .map { transitions ->
                        bargainStateMachine.foldOverTransitionsIntoState(NotFound(id), transitions)
                    }
            },
            { ad, user ->
                umlRenderer.append("WaitingForTransaction -> DeliveryService : start transaction\n") // cheating...
                umlRenderer.append("DeliveryService -> WaitingForTransaction : transactionId\n")
                Result.Success(TransactionId(-1))
            },
            bargainStateMachine,
        )
        val persistentHandler = PersistentCommandHandler(
            commandHandler,
            datasource::save
        )
        val dottyHandler = UmlCommandHandler(persistentHandler, umlRenderer)

        val handler = dottyHandler


        val entityId = handler.invoke(CreateBargain(AdId(1), UserId(2), 34)).orThrow().state.id
        handler.invoke(AcceptBargain(entityId, UserId(3))).orThrow()

        File("src/test/kotlin/example/bargain/command.puml")
            .writeText(umlRenderer.toUml())
    }
}

