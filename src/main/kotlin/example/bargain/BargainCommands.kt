package example.bargain

import commandhandler.Command
import commandhandler.CommandHandler
import functional.*
import functional.Result.Failure
import statemachine.*

sealed class BargainCommand : Command

data class CreateBargain(val ad: AdId, val buyer: UserId, val price: Int) : BargainCommand()
data class RejectBargain(val stateId: StateId, val user: UserId) : BargainCommand()
data class AcceptBargain(val stateId: StateId, val user: UserId) : BargainCommand()
data class StartTransaction(val stateId: StateId) : BargainCommand()


class BargainCommandHandler(
    private val find: (UserId, AdId) -> Result<ErrorCode, BargainState>,
    private val fetch: (StateId) -> Result<ErrorCode, BargainState>,
    private val startTransaction: (UserId, AdId) -> Result<ErrorCode, TransactionId>,
    private val stateMachine: StateMachine<BargainState, BargainEvent>,
) : CommandHandler<BargainCommand, BargainState, BargainEvent> {
    override fun invoke(command: BargainCommand): Result<ErrorCode, Application<BargainState, BargainEvent>> =
        when (command) {
            is CreateBargain -> command.perform()
            is RejectBargain -> command.perform()
            is AcceptBargain -> command.perform()
            is StartTransaction -> command.perform()
        }

    private fun CreateBargain.perform(): Result<ErrorCode, Application<BargainState, BargainEvent>> {
        return find(buyer, ad).flatMap {
            Failure(BargainAlreadyExists(ad, buyer))
        }.flatMapFailure {
            if (it is BargainNotFound) {
                stateMachine.applyTransition(NotFound(), SellerOfferedBargain(ad, buyer, price))
            } else Failure(it)
        }
    }

    private fun RejectBargain.perform(): Result<ErrorCode, Application<BargainState, BargainEvent>> {
        return fetch(stateId).flatMap {
            stateMachine.applyTransition(it, RejectedBargain(user))
        }
    }

    private fun AcceptBargain.perform(): Result<ErrorCode, Application<BargainState, BargainEvent>> {
        return fetch(stateId).flatMap { state ->
            stateMachine
                .applyTransition(state, AcceptedBargain(user))
                // should we separate this?
                .applyTransitionWithSingleSideEffect { updatedState: WaitingForTransaction ->
                    startTransaction(updatedState.buyer, updatedState.ad)
                        .map { TransactionStarted(it) }
                }
        }
    }

    private fun StartTransaction.perform(): Result<ErrorCode, Application<BargainState, BargainEvent>> {
        return fetch(stateId).flatMap { state ->
            stateMachine
                .applyTransitionWithSingleSideEffect(state) { updatedState: WaitingForTransaction ->
                    startTransaction(updatedState.buyer, updatedState.ad)
                        .map { TransactionStarted(it) }
                }
        }
    }
}

sealed class BargainCommandError : ErrorCode

data class BargainAlreadyExists(val ad: AdId, val buyer: UserId) : BargainCommandError()
data class BargainNotFound(val ad: AdId, val buyer: UserId) : BargainCommandError()