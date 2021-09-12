package example

import functional.ErrorCode
import statemachine.State
import statemachine.StateId
import statemachine.StateMachine
import statemachine.Transition
import java.math.BigDecimal
import java.util.*

val accountWorkflow = StateMachine<AccountState, AccountTransition>()
    .defineStateTransition { initialState: NotFound, created: Created ->
        NeedsWelcomeEmail(initialState.id, created.userEmail)
    }
    .defineStateTransition { needsWelcomeEmail: NeedsWelcomeEmail, messageSent: WelcomeMessageSent ->
        AccountOpen(needsWelcomeEmail.id, Money(BigDecimal.ZERO.apply { setScale(2) }))
    }
    .defineStateTransition { accountOpen: AccountOpen, updateBalance: UpdateBalance ->
        AccountOpen(accountOpen.id, updateBalance.balance)
    }
    .defineStateTransition { accountOpen: AccountOpen, overdraw: Overdraw ->
        Overdrawn(accountOpen.id, overdraw.balance)
    }
    .defineStateTransition { overdrawn: Overdrawn, updateBalance: UpdateBalance ->
        AccountOpen(overdrawn.id, updateBalance.balance)
    }

sealed class AccountState : State {
    abstract val id: StateId
}

data class NotFound(override val id: StateId = StateId(UUID.randomUUID())) : AccountState()
data class NeedsWelcomeEmail(override val id: StateId, val userEmail: Email) : AccountState()
data class AccountOpen(override val id: StateId, val balance: Money) : AccountState()
data class Overdrawn(override val id: StateId, val balance: Money) : AccountState()

sealed class AccountTransition : Transition

data class Created(val userEmail: Email) : AccountTransition()
data class WelcomeMessageSent(val transactionId: EmailTransactionId) : AccountTransition()
data class UpdateBalance(val balance: Money) : AccountTransition()
data class Overdraw(val balance: Money) : AccountTransition()


data class Email(val raw: String)
data class EmailTransactionId(val raw: String)
data class Money(val amount: BigDecimal)

data class EmailSendFailure(val reason: String) : ErrorCode