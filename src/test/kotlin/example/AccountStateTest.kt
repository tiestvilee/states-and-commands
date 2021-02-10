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
        val nextState = initialState
            .applyTransition(Created(emailAddress))
            .orThrow()

        assertEquals(
            ChainableApplication(
                NeedsWelcomeEmail(initialState.id, emailAddress),
                Created(emailAddress)
            ),
            nextState
        )

        val finalState = nextState.new.applyTransition(WelcomeMessageSent(emailTxn)).orThrow()

        assertEquals(
            ChainableApplication(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn)
            ),
            finalState
        )
    }

    @Test
    fun `invalid transition`() {
        val nextState = initialState
            .applyTransition(WelcomeMessageSent(emailTxn))

        assertEquals(
            failure(StateTransitionError(initialState, WelcomeMessageSent(emailTxn))),
            nextState
        )
    }

    @Test
    fun `dosomething in the transition`() {

        val nextState = NeedsWelcomeEmail(initialState.id, emailAddress)
            .applyTransition { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                success(WelcomeMessageSent(emailTxn))
            }.orThrow()

        assertEquals(
            ChainableApplication(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn)
            ),
            nextState
        )
    }

    @Test
    fun `don't dosomething because transition is invalid`() {

        var performedAction = false
        val nextState = initialState
            .applyTransition { initial: NotFound ->
                // do something that might fail - like send an email
                performedAction = true
                success(WelcomeMessageSent(emailTxn))
            }

        assertEquals(
            failure(StateTransitionClassError(initialState, WelcomeMessageSent::class)),
            nextState
        )

        assertFalse(performedAction, "shouldn't be called because transition is invalid")
    }

    @Test
    fun `state doesn't match expected state`() {

        var performedAction = false
        val nextState = initialState
            .applyTransition { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                performedAction = true
                success(WelcomeMessageSent(emailTxn))
            }

        assertEquals(
            failure(WrongStateError(initialState, NeedsWelcomeEmail::class)),
            nextState
        )

        assertFalse(performedAction, "shouldn't be called because state is invalid")
    }

    @Test
    fun `fail to dosomething in the transition`() {
        @Suppress("CanBeVal") var trickCompilerIntoReturningGoodType = true

        val nextState = NeedsWelcomeEmail(initialState.id, emailAddress)
            .applyTransition { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                if (trickCompilerIntoReturningGoodType)
                    failure(EmailSendFailure("invalid hostname"))
                else
                    success(WelcomeMessageSent(emailTxn))
            }


        assertEquals(
            failure(EmailSendFailure("invalid hostname")),
            nextState
        )
    }

    @Test
    fun `chained transitions`() {
        val nextState = initialState
            .applyTransition(Created(emailAddress))
            .applyTransition(WelcomeMessageSent(emailTxn))

        assertEquals(
            ChainableApplication(
                AccountOpen(initialState.id, openingBalance),
                WelcomeMessageSent(emailTxn),
                ChainableApplication(NeedsWelcomeEmail(initialState.id, emailAddress), Created(emailAddress))
            ),
            nextState.orThrow()
        )
    }

    @Test
    fun `chained to a doSomething`() {
        val nextState = initialState
            .applyTransition(Created(emailAddress))
            .applyTransition { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                success(WelcomeMessageSent(emailTxn))
            }.orThrow()

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
        ).fold(initialState as State, { state, transition ->
            state.applyTransition(transition).orThrow().new
        })

        assertEquals(AccountOpen(initialState.id, openingBalance), state)
    }
}