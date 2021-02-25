package statemachine

import functional.ErrorCode
import functional.Result
import functional.Result.Companion.failure
import functional.flatMap
import functional.map
import kotlin.reflect.KClass

sealed class Application<S : State, T : Transition>(
    open val state: S,
    open val applied: List<T>
)

data class ChainableApplication<S : State, T : Transition>(
    val stateMachine: StateMachine<S, T>,
    override val state: S,
    override val applied: List<T>
) : Application<S, T>(state, applied) {
    fun chain(state: S, transition: T) =
        ChainableApplication(stateMachine, state, applied + transition)

    fun endChain(state: S, transition: T) =
        FinalApplication(state, applied + transition)

}

data class FinalApplication<S : State, T : Transition>(
    override val state: S,
    override val applied: List<T>
) : Application<S, T>(state, applied)

fun <S : State, T : Transition> StateMachine<S, T>.applyTransition(
    state: S,
    transition: T
): Result<ErrorCode, ChainableApplication<S, T>> =
    nextState(state, transition)
        .map {
            ChainableApplication(this, it, listOf(transition))
        }

inline fun <S : State, T : Transition, reified S2 : S, reified T2 : T> StateMachine<S, T>.applyTransitionWithSingleSideEffect(
    state: S,
    tryThis: (S2) -> Result<ErrorCode, T2>
): Result<ErrorCode, FinalApplication<S, T>> =
    if (state is S2) {
        this.getTransitionFunction(state, T2::class)?.let { fn ->
            tryThis(state)
                .map { transition ->
                    FinalApplication(fn(state, transition), listOf(transition))
                }
        } ?: failure(InvalidTransitionClassForState(state, T2::class))
    } else {
        failure(IncompatibleStateWithFunction(state, S2::class))
    }

fun <S : State, T : Transition> Result<ErrorCode, ChainableApplication<S, T>>.applyTransition(transition: T): Result<ErrorCode, ChainableApplication<S, T>> =
    this.flatMap { chain ->
        chain.stateMachine.nextState(chain.state, transition)
            .map {
                chain.chain(it, transition)
            }
    }

inline fun <S : State, T : Transition, reified S2 : S, reified T2 : T> Result<ErrorCode, ChainableApplication<S, T>>.applyTransitionWithSingleSideEffect(
    tryThis: (S2) -> Result<ErrorCode, T2>
): Result<ErrorCode, FinalApplication<S, T>> =
    this.flatMap { chain ->
        if (chain.state is S2) {
            chain.stateMachine.getTransitionFunction(chain.state, T2::class)?.let { fn ->
                tryThis(chain.state)
                    .map { transition ->
                        chain.endChain(fn(chain.state, transition), transition)
                    }
            } ?: failure(InvalidTransitionClassForState(chain.state, T2::class))
        } else {
            failure(IncompatibleStateWithFunction(chain.state, S2::class))
        }
    }

data class IncompatibleStateWithFunction(val actualState: State, val expectedState: KClass<out State>) : ErrorCode
data class InvalidTransitionClassForState(val state: State, val transitionClass: KClass<out Transition>) : ErrorCode
