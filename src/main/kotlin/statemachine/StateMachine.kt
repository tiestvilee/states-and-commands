package statemachine

import functional.ErrorCode
import functional.Result
import functional.flatMap
import kotlin.reflect.KClass

interface State {
    fun applyTransition(
        transition: Transition,
        applied: ChainableApplication? = null
    ): Result<ErrorCode, ChainableApplication>
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

data class WrongStateError(val state: State) : ErrorCode
data class StateTransitionError(val state: State, val transition: Transition) : ErrorCode
data class StateTransitionClassError(val state: State, val transitionClass: KClass<out Transition>) : ErrorCode

fun Result<ErrorCode, ChainableApplication>.applyTransition(transition: Transition): Result<ErrorCode, ChainableApplication> {
    return this.flatMap {
        it.new.applyTransition(transition, it)
    }
}

