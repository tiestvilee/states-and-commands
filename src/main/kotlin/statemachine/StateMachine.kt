package statemachine

import functional.ErrorCode
import functional.Result
import functional.Result.Companion.failure
import functional.flatMap
import functional.map
import kotlin.reflect.KClass

abstract class State {

    abstract fun getTransitionFunction(transitionClass: KClass<out Transition>): ((State, Transition) -> State)?

    fun applyTransition(
        transition: Transition,
        applied: ChainableApplication? = null
    ): Result<ErrorCode, ChainableApplication> {
        return getTransitionFunction(transition::class)?.let {
            Result.success(ChainableApplication(it(this, transition), transition, applied))
        } ?: failure(StateTransitionError(this, transition))
    }

    inline fun <reified S : State, reified T : Transition> applyTransition(
        tryThis: (S) -> Result<ErrorCode, T>
    ): Result<ErrorCode, Application> {
        return if (this is S)
            getTransitionFunction(T::class)?.let { fn ->
                tryThis(this)
                    .map { transition ->
                        ChainableApplication(fn(this, transition), transition)
                    }
            } ?: failure(StateTransitionClassError(this, T::class))
        else
            failure(WrongStateError(this, S::class))
    }

    inline fun <reified S : State, reified T : Transition> applyTransition(
        applied: ChainableApplication? = null,
        tryThis: (S) -> Result<ErrorCode, T>
    ): Result<ErrorCode, Application> {
        return if (this is S)
            getTransitionFunction(T::class)?.let { fn ->
                tryThis(this)
                    .map { transition ->
                        ChainableApplication(fn(this, transition), transition, applied)
                    }
            } ?: failure(StateTransitionClassError(this, T::class))
        else
            failure(WrongStateError(this, S::class))
    }
}

interface Transition

open class Application(
    open val new: State,
    open val applied: Transition,
    open val chainedApplication: ChainableApplication? = null
)

data class ChainableApplication(
    override val new: State,
    override val applied: Transition,
    override val chainedApplication: ChainableApplication? = null
) : Application(new, applied, chainedApplication)

data class WrongStateError(val actualState: State, val expectedState: KClass<out State>) : ErrorCode
data class StateTransitionError(val state: State, val transition: Transition) : ErrorCode
data class StateTransitionClassError(val state: State, val transitionClass: KClass<out Transition>) : ErrorCode

fun Result<ErrorCode, ChainableApplication>.applyTransition(transition: Transition): Result<ErrorCode, ChainableApplication> {
    return this.flatMap { chainableApplication ->
        chainableApplication.new.applyTransition(transition, chainableApplication)
    }
}


inline fun <reified S : State, reified T : Transition> Result<ErrorCode, ChainableApplication>.applyTransition(noinline tryThis: (S) -> Result<ErrorCode, T>): Result<ErrorCode, Application> {
    return this.flatMap { application ->
        if (application.new is S) {
            application.new.applyTransition(application, tryThis)
        } else {
            failure(WrongStateError(application.new, S::class))
        }
    }
}