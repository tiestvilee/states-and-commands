package statemachine

import functional.ErrorCode
import functional.Result
import functional.flatMap
import kotlin.reflect.KClass

interface State {
    fun applyTransition(
        transition: Transition,
        applied: List<Transition> = emptyList()
    ): Result<ErrorCode, ChainableApplication>
}

interface Transition

open class Application(val new: State, val applied: List<Transition>)
class ChainableApplication(new: State, applied: List<Transition>) : Application(new, applied)

data class WrongStateError(val state: State) : ErrorCode
data class StateTransitionError(val state: State, val transition: Transition) : ErrorCode
data class StateTransitionClassError(val state: State, val transitionClass: KClass<out Transition>) : ErrorCode

fun Result<ErrorCode, ChainableApplication>.applyTransition(transition: Transition): Result<ErrorCode, ChainableApplication> {
    return this.flatMap {
        it.new.applyTransition(transition, it.applied)
    }
}

