package statemachine

import functional.ErrorCode
import functional.Result
import functional.flatMap
import functional.map

fun <S : State, T : Transition> StateMachine<S, T>.applyTransition(
    state: S,
    transition: T
): Result<ErrorCode, ChainableApplication<S, T>> =
    nextState(state, transition)
        .map {
            ChainableApplication(it, listOf(transition))
        }

inline fun <S : State, T : Transition, S2 : S, reified T2 : T> StateMachine<S, T>.applyTransitionWithSideEffect(
    state: S2,
    tryThis: (S2) -> Result<ErrorCode, T2>
): Result<ErrorCode, Application<S, T>> =
    this.getTransitionFunction(state, T2::class)?.let { fn ->
        tryThis(state)
            .map { transition ->
                ChainableApplication(fn(state, transition), listOf(transition))
            }
    } ?: Result.failure(StateTransitionClassError(state, T2::class))

open class Application<S : State, T : Transition>(
    open val state: S,
    open val applied: List<T>
) {
    fun flattenTransitions(): List<T> = applied
}

data class ChainableApplication<S : State, T : Transition>(
    override val state: S,
    override val applied: List<T>
) : Application<S, T>(state, applied)

fun <S : State, T : Transition> Result<ErrorCode, ChainableApplication<S, T>>.applyTransition(
    stateMachine: StateMachine<S, T>,
    transition: T
): Result<ErrorCode, ChainableApplication<S, T>> =
    this.flatMap { chain ->
        stateMachine.nextState(chain.state, transition)
            .map {
                ChainableApplication(it, chain.applied + transition)
            }
    }

inline fun <S : State, T : Transition, reified S2 : S, reified T2 : T> Result<ErrorCode, ChainableApplication<S, T>>.applyTransitionWithSideEffect(
    stateMachine: StateMachine<S, T>,
    tryThis: (S2) -> Result<ErrorCode, T2>
): Result<ErrorCode, Application<S, T>> =
    this.flatMap {
        if (it.state is S2) {
            stateMachine.getTransitionFunction(it.state, T2::class)?.let { fn ->
                tryThis(it.state)
                    .map { transition ->
                        ChainableApplication(fn(it.state, transition), it.applied + transition)
                    }
            } ?: Result.failure(StateTransitionClassError(it.state, T2::class))
        } else {
            Result.failure(WrongStateError(it.state, S2::class))
        }
    }