package example

import functional.ErrorCode
import functional.Result
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
    fun `individual transitions`() {
        val application = accountWorkflow.applyTransition(initialState, Created(emailAddress))
            .orThrow()

        assertEquals(
            ChainableApplication(
                accountWorkflow,
                NeedsWelcomeEmail(initialState.id, emailAddress),
                listOf(Created(emailAddress))
            ),
            application
        )

        val finalState = accountWorkflow.applyTransition(application.state, WelcomeMessageSent(emailTxn)).orThrow()

        assertEquals(
            ChainableApplication(
                accountWorkflow,
                AccountOpen(initialState.id, openingBalance),
                listOf(WelcomeMessageSent(emailTxn))
            ),
            finalState
        )
    }

    @Test
    fun `invalid transition`() {
        val nextState = accountWorkflow.applyTransition(initialState, WelcomeMessageSent(emailTxn))

        assertEquals(
            failure(InvalidTransitionForState(initialState, WelcomeMessageSent(emailTxn))),
            nextState
        )
    }

    @Test
    fun `dosomething in the transition`() {

        val nextState =
            accountWorkflow.applyTransitionWithSingleSideEffect(NeedsWelcomeEmail(initialState.id, emailAddress),
                { _: NeedsWelcomeEmail ->
                    // do something that might fail - like send an email
                    success(WelcomeMessageSent(emailTxn))
                }).orThrow()

        assertEquals(
            FinalApplication(
                AccountOpen(initialState.id, openingBalance),
                listOf(WelcomeMessageSent(emailTxn))
            ),
            nextState
        )
    }

    @Test
    fun `don't dosomething because transition is invalid`() {

        var performedAction = false
        val nextState = accountWorkflow.applyTransitionWithSingleSideEffect(initialState, { initial: NotFound ->
            // do something that might fail - like send an email
            performedAction = true
            success(WelcomeMessageSent(emailTxn))
        })

        assertEquals(
            failure(InvalidTransitionClassForState(initialState, WelcomeMessageSent::class)),
            nextState
        )

        assertFalse(performedAction, "shouldn't be called because transition is invalid")
    }

    @Test
    fun `state of sied effect function doesn't match expected state causes compile time error`() {
        val tryThis: (NeedsWelcomeEmail) -> Result<ErrorCode, WelcomeMessageSent> =
            { needsWelcomeEmail: NeedsWelcomeEmail ->
                success(WelcomeMessageSent(emailTxn))
            }
        // shouldn't compile
//        accountWorkflow.applyTransitionWithSideEffect(initialState, tryThis)
    }

    @Test
    fun `emitting more than one type of event from side-effect function causes an error`() {
        // force upcasting of Transition
        val tryThis: (NotFound) -> Result<ErrorCode, AccountTransition> = { initialState: NotFound ->
            success(Created(emailAddress))
        }

        // shouldn't compile, the Result has been upcast.
        // not possible. only works at Runtime :(
        val failure = accountWorkflow.applyTransitionWithSingleSideEffect(initialState, tryThis)

        assertEquals(
            failure(InvalidTransitionClassForState(initialState, AccountTransition::class)),
            failure
        )
    }

    @Test
    fun `fail to dosomething in the transition`() {
        @Suppress("CanBeVal") var trickCompilerIntoReturningGoodType = true

        val nextState =
            accountWorkflow.applyTransitionWithSingleSideEffect(NeedsWelcomeEmail(initialState.id, emailAddress),
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
            .applyTransition(WelcomeMessageSent(emailTxn))
            .orThrow()

        assertEquals(
            ChainableApplication(
                accountWorkflow,
                AccountOpen(initialState.id, openingBalance),
                listOf(Created(emailAddress), WelcomeMessageSent(emailTxn))
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
            .applyTransition(Created(emailAddress))

        assertEquals(
            failure(
                InvalidTransitionForState(
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
            .applyTransitionWithSingleSideEffect { needsWelcomeEmail: NeedsWelcomeEmail ->
                // do something that might fail - like send an email
                success(WelcomeMessageSent(emailTxn))
            }.orThrow()

        assertEquals(
            FinalApplication(
                AccountOpen(initialState.id, openingBalance),
                listOf(Created(emailAddress), WelcomeMessageSent(emailTxn))
            ),
            nextState
        )
    }

    @Test
    fun `fold to get state`() {
        val state = accountWorkflow.foldOverTransitionsIntoState(
            initialState,
            listOf(
                Created(emailAddress),
                WelcomeMessageSent(emailTxn)
            )
        )

        assertEquals(AccountOpen(initialState.id, openingBalance), state)
    }
}