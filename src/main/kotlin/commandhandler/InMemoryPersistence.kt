package commandhandler

import functional.ErrorCode
import functional.Result
import functional.asSuccess
import statemachine.StateId
import statemachine.Transition

class InMemoryPersistence<T : Transition> {
    val data: MutableList<Pair<StateId, T>> = mutableListOf()

    fun save(id: StateId, transition: T) {
        data += Pair(id, transition)
    }

    fun fetchTransitionsFor(id: StateId): Result<ErrorCode, List<T>> {
        return data.filter { it.first == id }.map { it.second }.asSuccess()
    }

    fun <S> fetchTransitionsForAllStates(param: (StateId, List<T>) -> S): List<S> {
        return data.fold(mapOf<StateId, List<T>>()) { acc, pair ->
            val orig = acc.getOrDefault(pair.first, emptyList())
            acc + (pair.first to (orig + pair.second))
        }.map {
            param(it.key, it.value)
        }
    }
}