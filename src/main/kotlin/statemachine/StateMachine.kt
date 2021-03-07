package statemachine

import functional.ErrorCode
import functional.Result
import functional.Result.Companion.failure
import functional.Result.Companion.success
import functional.orThrow
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

    fun nextState(state: S, transition: T): Result<ErrorCode, S> =
        getTransitionFunction(state, transition::class)?.let {
            success(it(state, transition))
        } ?: failure(InvalidTransitionForState(state, transition))

    fun <S2 : S, T2 : T> foldOverTransitionsIntoState(initialState: S2, transitions: List<T2>) =
        transitions.fold(initialState as S,
            { state, transition ->
                nextState(state, transition).orThrow()
            })

    @Suppress("UNCHECKED_CAST")
    inline fun <reified S2 : S, reified T2 : T, S3 : S> defineStateTransition(noinline function: (S2, T2) -> S3): StateMachine<S, T> {
        val stateClass: KClass<S> = S2::class as KClass<S>
        val transitionClass: KClass<T> = T2::class as KClass<T>
        val newStateTransition: Pair<Pair<KClass<S>, KClass<T>>, (S, T) -> S> =
            Pair(Pair(stateClass, transitionClass), function as (S, T) -> S)
        return StateMachine(stateTransitionTable + newStateTransition)
    }
}

data class InvalidTransitionForState(val state: State, val transition: Transition) : ErrorCode

fun <S : State, T : Transition> StateMachine<S, T>.dot(): String {
    return """digraph {
    |  pad=0.4;
    |  nodesep=0.5;
    |  ranksep=0.8;
    |${
        stateTransitionTable.entries.flatMap {
            it.value::class.java.declaredMethods.toList()
        }
            .filterNot { it.parameterTypes[0].isInstance(State::class.java) }
            .joinToString("\n") { """  ${it.parameterTypes[0].simpleName} -> ${it.returnType.simpleName} [label="${it.parameterTypes[1].simpleName}"]""" }
    }
    |}""".trimMargin()
}