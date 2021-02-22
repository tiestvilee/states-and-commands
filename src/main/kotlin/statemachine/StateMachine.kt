package statemachine

import functional.*
import functional.Result.Companion.failure
import java.util.*
import kotlin.reflect.KClass

data class StateId(val id: UUID)

interface State
interface Transition

class StateMachine<S : State, T : Transition>(
    val stateTransitionTable: Map<Pair<KClass<out S>, KClass<out T>>, (S, T) -> S> = emptyMap()
) {

    fun getTransitionFunction(state: S, transitionClass: KClass<out T>): ((S, T) -> S)? {
        return stateTransitionTable[Pair(state::class, transitionClass)]
    }

    fun applyTransition(
        state: S,
        transition: T,
        applied: ChainableApplication<S, T>? = null
    ): Result<ErrorCode, ChainableApplication<S, T>> {
        return getTransitionFunction(state, transition::class)?.let {
            Result.success(ChainableApplication(it(state, transition), transition, applied))
        } ?: failure(StateTransitionError(state, transition))
    }

    inline fun <S2 : S, reified T2 : T> applyTransition2(
        state: S2,
        tryThis: (S2) -> Result<ErrorCode, T2>,
        applied: ChainableApplication<S, T>? = null
    ): Result<ErrorCode, Application<S, T>> {
        return getTransitionFunction(state, T2::class)?.let { fn ->
            tryThis(state)
                .map { transition ->
                    ChainableApplication(fn(state, transition), transition, applied)
                }
        } ?: failure(StateTransitionClassError(state, T2::class))
    }

    fun <S2 : S, T2 : T> foldOverTransitionsIntoState(initialState: S2, transitions: List<T2>) =
        transitions.fold(initialState as S,
            { state, transition ->
                applyTransition(state, transition).orThrow().new
            })

    @Suppress("UNCHECKED_CAST")
    inline fun <reified S2 : S, reified T2 : T> defineStateTransition(noinline function: (S2, T2) -> S): StateMachine<S, T> {
        val stateClass: KClass<S> = S2::class as KClass<S>
        val transitionClass: KClass<T> = T2::class as KClass<T>
        val newStateTransition: Pair<Pair<KClass<S>, KClass<T>>, (S, T) -> S> =
            Pair(Pair(stateClass, transitionClass), function as (S, T) -> S)
        return StateMachine(stateTransitionTable + newStateTransition)
    }
}


open class Application<S : State, T : Transition>(
    open val new: S,
    open val applied: T,
    open val chainedApplication: ChainableApplication<S, T>? = null
) {
    fun flattenTransitions(): List<T> = (chainedApplication?.flattenTransitions() ?: emptyList()) + applied
}

data class ChainableApplication<S : State, T : Transition>(
    override val new: S,
    override val applied: T,
    override val chainedApplication: ChainableApplication<S, T>? = null
) : Application<S, T>(new, applied, chainedApplication)

fun <S : State, T : Transition> ChainableApplication<S, T>.applyTransition(
    stateMachine: StateMachine<S, T>,
    transition: T
): Result<ErrorCode, ChainableApplication<S, T>> =
    stateMachine.applyTransition(this.new, transition, this)

fun <S : State, T : Transition> Result<ErrorCode, ChainableApplication<S, T>>.applyTransition(
    stateMachine: StateMachine<S, T>,
    transition: T
): Result<ErrorCode, ChainableApplication<S, T>> =
    this.flatMap { stateMachine.applyTransition(it.new, transition, it) }

inline fun <S : State, T : Transition, reified S2 : S, reified T2 : T> Result<ErrorCode, ChainableApplication<S, T>>.applyTransition2(
    stateMachine: StateMachine<S, T>,
    tryThis: (S2) -> Result<ErrorCode, T2>
): Result<ErrorCode, Application<S, T>> =
    this.flatMap {
        if (it.new is S2) {
            stateMachine.applyTransition2(it.new, tryThis, it)
        } else {
            failure(WrongStateError(it.new, S2::class))
        }
    }

data class WrongStateError(val actualState: State, val expectedState: KClass<out State>) : ErrorCode
data class StateTransitionError(val state: State, val transition: Transition) : ErrorCode
data class StateTransitionClassError(val state: State, val transitionClass: KClass<out Transition>) : ErrorCode
