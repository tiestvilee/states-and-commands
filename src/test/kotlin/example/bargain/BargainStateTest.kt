package example.bargain

import org.junit.Assert
import org.junit.Test
import statemachine.puml
import java.io.File

class BargainStateTest {

    @Test
    fun `successfully executes a task and its extensions`() {
//        val firstTask = Barga(Instant.now().plusSeconds(5), URI.create("http://do.this/1"))
//        val secondTask = ScheduledTask(Instant.now().plusSeconds(10), URI.create("http://do.this/2"))
//        val initialState = NotFound()
//        val endState = scheduledTaskWorkflow.applyTransition(initialState, ScheduledTaskWorkflowCreated(firstTask))
//            .applyTransition(TaskStarted)
//            .applyTransition(TaskExtended(secondTask))
//            .applyTransition(TaskStarted)
//            .applyTransition(TaskCompleted)
//            .orThrow()
//
//        assertEquals(CompleteTask(initialState.id), endState.state)
//        assertEquals(
//            listOf(
//                ScheduledTaskWorkflowCreated(firstTask),
//                TaskStarted,
//                TaskExtended(secondTask),
//                TaskStarted,
//                TaskCompleted,
//            ), endState.applied
//        )
    }

    @Test
    fun `outputs dot graph`() {
        val uml = bargainStateMachine.puml()
        File("src/test/kotlin/example/bargain/states.puml")
            .writeText(uml)

        Assert.assertEquals(
            """@startuml
  [*] --> NotFound
  NotFound --> WaitingForAcceptance : SellerOfferedBargain
  WaitingForAcceptance --> Rejected : RejectedBargain
  WaitingForAcceptance --> WaitingForTransaction : AcceptedBargain
  WaitingForTransaction --> TransactionCreated : TransactionStarted
  WaitingForTransaction --> Rejected : RejectedBargain
  Rejected --> [*]
  TransactionCreated --> [*]
@enduml""",
            uml
        )

    }
}