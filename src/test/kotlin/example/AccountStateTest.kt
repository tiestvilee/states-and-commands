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

    @Test
    fun `individual transistions`() {
        val nextState = InitialState
            .applyTransition(Created(emailAddress))
            .orThrow()

        assertEquals(
            ChainableApplication(
                NeedsWelcomeEmail(emailAddress),
                listOf(Created(emailAddress))
            ),
            nextState
        )

        val finalState = nextState.new.applyTransition(WelcomeMessageSent(emailTxn)).orThrow()

        assertEquals(
            ChainableApplication(
                AccountOpen(openingBalance),
                listOf(WelcomeMessageSent(emailTxn))
            ),
            finalState
        )
    }

    @Test
    fun `chained transitions`() {
        val nextState = InitialState
            .applyTransition(Created(emailAddress))
            .applyTransition(WelcomeMessageSent(emailTxn))

        assertEquals(
            ChainableApplication(
                AccountOpen(openingBalance),
                listOf(Created(emailAddress), WelcomeMessageSent(emailTxn))
            ),
            nextState.orThrow()
        )
    }

    @Test
    fun `invalid transition`() {
        val nextState = InitialState
            .applyTransition(WelcomeMessageSent(emailTxn))

        assertEquals(
            failure(StateTransitionError(InitialState, WelcomeMessageSent(emailTxn))),
            nextState
        )
    }

    @Test
    fun `dosomething in the transition`() {

        val nextState = NeedsWelcomeEmail(emailAddress)
            .applyTransition { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                success(WelcomeMessageSent(emailTxn))
            }.orThrow()

        assertEquals(
            ChainableApplication(
                AccountOpen(openingBalance),
                listOf(WelcomeMessageSent(emailTxn))
            ),
            nextState
        )
    }

    @Test
    fun `don't dosomething because transition is invalid`() {

        var performedAction = false
        val nextState = InitialState
            .applyTransition { initial: InitialState ->
                // do something that might fail - like send an email
                performedAction = true
                success(WelcomeMessageSent(emailTxn))
            }

        assertEquals(
            failure(StateTransitionClassError(InitialState, WelcomeMessageSent::class)),
            nextState
        )

        assertFalse(performedAction, "shouldn't be called because transition is invalid")
    }

    @Test
    fun `state doesn't match expected state`() {

        var performedAction = false
        val nextState = InitialState
            .applyTransition { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                performedAction = true
                success(WelcomeMessageSent(emailTxn))
            }

        assertEquals(
            failure(WrongStateError(InitialState)),
            nextState
        )

        assertFalse(performedAction, "shouldn't be called because state is invalid")
    }

    @Test
    fun `fail to dosomething in the transition`() {
        @Suppress("CanBeVal") var trickCompilerIntoReturningGoodType = true

        val nextState = NeedsWelcomeEmail(emailAddress)
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

}