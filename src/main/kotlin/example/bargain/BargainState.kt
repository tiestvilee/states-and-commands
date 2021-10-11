package example.bargain

import commandhandler.StateWithId
import statemachine.StateId
import statemachine.StateMachine
import statemachine.Transition
import java.util.*

/* domain objects */
data class AdId(val id: Int)
data class UserId(val id: Int)
data class TransactionId(val id: Int)

/* Workflow */

val bargainStateMachine = StateMachine<BargainState, BargainEvent>()
    .defineStateTransition { initialState: NotFound, offered: SellerOfferedBargain ->
        WaitingForAcceptance(initialState.id, offered.ad, offered.buyer, offered.price)
    }
    .defineStateTransition { waiting: WaitingForAcceptance, rejected: RejectedBargain ->
        Rejected(waiting.id, rejected.user)
    }
    .defineStateTransition { waiting: WaitingForAcceptance, _: AcceptedBargain ->
        WaitingForTransaction(waiting.id, waiting.ad, waiting.buyer, waiting.price)
    }
    .defineStateTransition { waiting: WaitingForTransaction, started: TransactionStarted ->
        TransactionCreated(waiting.id, waiting.ad, waiting.buyer, waiting.price, started.transactionId)
    }
    .defineStateTransition { waiting: WaitingForTransaction, rejected: RejectedBargain ->
        Rejected(waiting.id, rejected.user)
    }

/* States */
sealed class BargainState : StateWithId()

data class NotFound(override val id: StateId = StateId(UUID.randomUUID())) : BargainState()
data class WaitingForAcceptance(override val id: StateId, val ad: AdId, val buyer: UserId, val price: Int) :
    BargainState()

data class WaitingForTransaction(override val id: StateId, val ad: AdId, val buyer: UserId, val price: Int) :
    BargainState()

data class Rejected(override val id: StateId, val user: UserId) : BargainState()
data class TransactionCreated(
    override val id: StateId,
    val ad: AdId,
    val buyer: UserId,
    val price: Int,
    val transactionId: TransactionId
) : BargainState()


/* Transitions */

sealed class BargainEvent : Transition

data class SellerOfferedBargain(val ad: AdId, val buyer: UserId, val price: Int) : BargainEvent()
data class RejectedBargain(val user: UserId) : BargainEvent()
data class AcceptedBargain(val user: UserId) : BargainEvent()
data class TransactionStarted(val transactionId: TransactionId) : BargainEvent()
