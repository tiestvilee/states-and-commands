package example

import functional.ErrorCode
import statemachine.State
import statemachine.StateId
import statemachine.Transition
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass

sealed class AccountState : State() {
    companion object {

        val stateTransitionTable =
            mutableMapOf<Pair<KClass<out State>, KClass<out Transition>>, (State, Transition) -> State>()

        @Suppress("UNCHECKED_CAST")
        inline fun <reified S : State, reified T : Transition, S2 : State> defineStateTransition(noinline transform: (S, T) -> S2) {
            stateTransitionTable[Pair(S::class, T::class)] = transform as (State, Transition) -> State
        }

        init {
            defineStateTransition { intialState: InitialState, created: Created ->
                NeedsWelcomeEmail(
                    intialState.id,
                    created.userEmail
                )
            }
            defineStateTransition { needsWelcomeEmail: NeedsWelcomeEmail, messageSent: WelcomeMessageSent ->
                AccountOpen(needsWelcomeEmail.id, Money(BigDecimal.ZERO.apply { setScale(2) }))
            }
            defineStateTransition { accountOpen: AccountOpen, updateBalance: UpdateBalance ->
                AccountOpen(accountOpen.id, updateBalance.balance)
            }
            defineStateTransition { accountOpen: AccountOpen, overdraw: Overdraw ->
                Overdrawn(accountOpen.id, overdraw.balance)
            }
            defineStateTransition { overdrawn: Overdrawn, updateBalance: UpdateBalance ->
                Overdrawn(overdrawn.id, updateBalance.balance)
            }
        }
    }

    override fun getTransitionFunction(transitionClass: KClass<out Transition>) =
        stateTransitionTable[Pair(this::class, transitionClass)]
}

data class InitialState(override val id: StateId = StateId(UUID.randomUUID())) : AccountState()
//data class ErrorState(val current: State, val transition: Transition) : AccountState()

data class NeedsWelcomeEmail(override val id: StateId, val userEmail: Email) : AccountState()
data class AccountOpen(override val id: StateId, val balance: Money) : AccountState()
data class Overdrawn(override val id: StateId, val balance: Money) : AccountState()


data class Created(val userEmail: Email) : Transition
data class WelcomeMessageSent(val transactionId: EmailTransactionId) : Transition
data class UpdateBalance(val balance: Money) : Transition
data class Overdraw(val balance: Money) : Transition


data class Email(val raw: String)
data class EmailTransactionId(val raw: String)
data class Money(val amount: BigDecimal)

data class EmailSendFailure(val reason: String) : ErrorCode