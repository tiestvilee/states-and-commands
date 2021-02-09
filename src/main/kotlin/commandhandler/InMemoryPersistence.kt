package commandhandler

import functional.ErrorCode
import functional.Result
import functional.asSuccess
import statemachine.StateId
import statemachine.Transition

class InMemoryPersistence {
    val data: MutableList<Pair<StateId, Transition>> = mutableListOf()

    fun save(id: StateId, transition: Transition) {
        data += Pair(id, transition)
    }

    fun fetchTransitionsFor(id: StateId): Result<ErrorCode, List<Transition>> {
        return data.filter { it.first == id }.map { it.second }.asSuccess()
    }
}