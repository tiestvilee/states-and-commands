package example

import functional.Result.Companion.failure
import functional.Result.Companion.success
import functional.orThrow
import org.junit.Test
import statemachine.*
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountStateTest {
    private val emailTxn = EmailTransactionId("12345")
    private val emailAddress = Email("blah@you.com")
    private val openingBalance = Money(BigDecimal.ZERO.apply { setScale(2) })

    private val initialState = NotFound()

    @Test
    fun `individual transistions`() {
        val nextState = accountWorkflow.applyTransition(initialState, Created(emailAddress))
            .orThrow()

        assertEquals(
            ChainableApplication<AccountState, AccountTransition>(
                NeedsWelcomeEmail(initialState.id, emailAddress),
                Created(emailAddress)
            ),
            nextState
        )

        val finalState = accountWorkflow.applyTransition(nextState.new, WelcomeMessageSent(emailTxn)).orThrow()

        assertEquals(
            ChainableApplication<AccountState, AccountTransition>(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn)
            ),
            finalState
        )
    }

    @Test
    fun `invalid transition`() {
        val nextState = accountWorkflow.applyTransition(initialState, WelcomeMessageSent(emailTxn))

        assertEquals(
            failure(StateTransitionError(initialState, WelcomeMessageSent(emailTxn))),
            nextState
        )
    }

    @Test
    fun `dosomething in the transition`() {

        val nextState = accountWorkflow.applyTransition2(NeedsWelcomeEmail(initialState.id, emailAddress),
            { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                success(WelcomeMessageSent(emailTxn))
            }).orThrow()

        assertEquals(
            ChainableApplication<AccountState, AccountTransition>(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn)
            ),
            nextState
        )
    }

    @Test
    fun `don't dosomething because transition is invalid`() {

        var performedAction = false
        val nextState = accountWorkflow.applyTransition2(initialState, { initial: NotFound ->
            // do something that might fail - like send an email
            performedAction = true
            success(WelcomeMessageSent(emailTxn))
        })

        assertEquals(
            failure(StateTransitionClassError(initialState, WelcomeMessageSent::class)),
            nextState
        )

        assertFalse(performedAction, "shouldn't be called because transition is invalid")
    }

    @Test
    fun `state doesn't match expected state`() {
// shouldn't compile
//        accountWorkflow.applyTransition(initialState,
//             { needsWelcomeEmail: InitialState ->
//                // do something that might fail - like send an email
//                success(WelcomeMessageSent(emailTxn))
//            })
    }

    @Test
    fun `fail to dosomething in the transition`() {
        @Suppress("CanBeVal") var trickCompilerIntoReturningGoodType = true

        val nextState = accountWorkflow.applyTransition2(NeedsWelcomeEmail(initialState.id, emailAddress),
            { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                if (trickCompilerIntoReturningGoodType)
                    failure(EmailSendFailure("invalid hostname"))
                else
                    success(WelcomeMessageSent(emailTxn))
            })


        assertEquals(
            failure(EmailSendFailure("invalid hostname")),
            nextState
        )
    }

    @Test
    fun `chained transitions`() {
        val nextState = accountWorkflow
            .applyTransition(initialState, Created(emailAddress))
            .applyTransition(accountWorkflow, WelcomeMessageSent(emailTxn))
            .orThrow()

        assertEquals(
            ChainableApplication(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn),
                ChainableApplication(NeedsWelcomeEmail(initialState.id, emailAddress), Created(emailAddress))
            ),
            nextState
        )
    }

    @Test
    fun `badly chained transitions`() {
        val nextState = accountWorkflow
            .applyTransition(initialState, Created(emailAddress))
            // there's no way we can know what State came out of the first transition
            // so the validity of the second transition can only be determined at runtime
            .applyTransition(accountWorkflow, Created(emailAddress))

        assertEquals(
            failure(
                StateTransitionError(
                    NeedsWelcomeEmail(initialState.id, emailAddress),
                    Created(emailAddress)
                )
            ),
            nextState
        )
    }

    @Test
    fun `chained to a doSomething`() {
        val nextState = accountWorkflow
            .applyTransition(initialState, Created(emailAddress))
            .applyTransition2(accountWorkflow, { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                success(WelcomeMessageSent(emailTxn))
            }).orThrow()

        assertEquals(
            ChainableApplication(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn),
                ChainableApplication(NeedsWelcomeEmail(initialState.id, emailAddress), Created(emailAddress))
            ),
            nextState
        )
    }

    @Test
    fun `fold to get state`() {
        val state = listOf(
            Created(emailAddress),
            WelcomeMessageSent(emailTxn)
        ).fold(initialState as AccountState, { state, transition ->
            accountWorkflow.applyTransition(state, transition).orThrow().new
        })

        assertEquals(AccountOpen(initialState.id, openingBalance), state)
    }
}