package example

import functional.ErrorCode
import functional.Result
import functional.Result.Companion.failure
import functional.Result.Companion.success
import functional.map
import statemachine.*
import java.math.BigDecimal
import kotlin.reflect.KClass

sealed class AccountState : State {
    companion object {

        val stateTransitionTable =
            mutableMapOf<Pair<KClass<out AccountState>, KClass<out Transition>>, (AccountState, Transition) -> AccountState>()

        @Suppress("UNCHECKED_CAST")
        inline fun <reified S : AccountState, reified T : Transition, S2 : AccountState> defineStateTransition(noinline transform: (S, T) -> S2) {
            stateTransitionTable[Pair(S::class, T::class)] = transform as (AccountState, Transition) -> AccountState
        }

        init {
            defineStateTransition { _: InitialState, created: Created -> NeedsWelcomeEmail(created.userEmail) }
            defineStateTransition { needsWelcomeEmail: NeedsWelcomeEmail, messageSent: WelcomeMessageSent ->
                AccountOpen(Money(BigDecimal.ZERO.apply { setScale(2) }))
            }
        }
    }

    override fun applyTransition(
        transition: Transition,
        applied: List<Transition>
    ): Result<ErrorCode, ChainableApplication> {
        return stateTransitionTable[Pair(this::class, transition::class)]?.let {
            success(ChainableApplication(it(this, transition), applied + transition))
        } ?: failure(StateTransitionError(this, transition))
    }

    inline fun <reified S : State, reified T : Transition> applyTransition(tryThis: (S) -> Result<ErrorCode, T>): Result<ErrorCode, Application> {
        return if (this is S)
            stateTransitionTable[Pair(this::class, T::class)]?.let { fn ->
                tryThis(this)
                    .map { transition ->
                        ChainableApplication(fn(this, transition), listOf(transition))
                    }
            } ?: failure(StateTransitionClassError(this, T::class))
        else
            failure(WrongStateError(this))
    }

}

object InitialState : AccountState()
data class ErrorState(val current: State, val transition: Transition) : AccountState()

data class NeedsWelcomeEmail(val userEmail: Email) : AccountState()
data class AccountOpen(val balance: Money) : AccountState()


data class Created(val userEmail: Email) : Transition
data class WelcomeMessageSent(val transactionId: EmailTransactionId) : Transition


data class Email(val raw: String)
data class EmailTransactionId(val raw: String)
data class Money(val amount: BigDecimal)

data class EmailSendFailure(val reason: String) : ErrorCode