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

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T2 : T> defineStateTransitions(
        states: List<KClass<out S>>,
        noinline function: (S, T2) -> S
    ): StateMachine<S, T> {
        val transitions: List<Pair<Pair<KClass<S>, KClass<T>>, (S, T) -> S>> = states.map { state ->
            Pair(Pair(state, T2::class), function) as Pair<Pair<KClass<S>, KClass<T>>, (S, T) -> S>
        }.toList()
        return StateMachine(stateTransitionTable + transitions)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <S1 : S, reified T2 : T, S3 : S> forAllStatesAddTransition(
        except: List<KClass<out S>> = emptyList(),
        noinline function: (S1, T2) -> S3
    ): StateMachine<S, T> {
        val transitions = stateTransitionTable.keys
            .asSequence()
            .map { it.first }
            .toSet()
            .filterNot { except.contains(it) }
            .fold(stateTransitionTable) { table, state ->
                table + Pair(Pair(state, T2::class), function as (S, T) -> S)
            }
        return StateMachine(transitions)
    }
}

data class InvalidTransitionForState(val state: State, val transition: Transition) : ErrorCode

fun <S : State, T : Transition> StateMachine<S, T>.puml(): String {
    // find entry point
    val statesWithOutgoingTransitions: MutableSet<Class<*>> = mutableSetOf()
    val statesWithIncomingTransitions: MutableSet<Class<*>> = mutableSetOf()

    stateTransitionTable.entries.forEach { entry ->
        statesWithIncomingTransitions += entry.value::class.java.declaredMethods.toList()
            .filterNot { it.parameterTypes[0].isInstance(State::class.java) }
            .map { it.returnType }
            .first()
        statesWithOutgoingTransitions += entry.key.first.javaObjectType
    }

    println(statesWithIncomingTransitions)
    println(statesWithOutgoingTransitions)

    val firstStates = statesWithOutgoingTransitions - statesWithIncomingTransitions
    val lastStates = statesWithIncomingTransitions - statesWithOutgoingTransitions

    return """@startuml
        |${
        firstStates.map {
            """  [*] --> ${it.simpleName}"""
        }.joinToString("\n")
    }
        |${
        stateTransitionTable.entries.map { entry ->
            Pair(
                entry.key,
                entry.value::class.java.declaredMethods.toList()
                    .filterNot { it.parameterTypes[0].isInstance(State::class.java) }
                    .map { it.returnType.simpleName }
                    .first()
            )
        }
            .joinToString("\n") { pair -> """  ${pair.first.first.simpleName} --> ${pair.second} : ${pair.first.second.simpleName}""" }
    }
    |${
        lastStates.map {
            """  ${it.simpleName} --> [*]"""
        }.joinToString("\n")
    }
        |@enduml""".trimMargin()
}