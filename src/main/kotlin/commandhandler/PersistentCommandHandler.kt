package commandhandler

import functional.ErrorCode
import functional.Result
import functional.onEach
import statemachine.Application
import statemachine.State
import statemachine.StateId
import statemachine.Transition

abstract class StateWithId : State {
    abstract val id: StateId
}

class PersistentCommandHandler<C : Command, S : StateWithId, T : Transition>(
    val delegate: CommandHandler<C, S, T>,
    val saveTransition: (StateId, T) -> Unit
) : CommandHandler<C, S, T> {

    override fun invoke(command: C): Result<ErrorCode, Application<S, T>> {
        return delegate.invoke(command)
            .onEach { application ->
                application.flattenTransitions()
                    .map {
                        saveTransition(application.new.id, it)
                    }
            }
    }
}