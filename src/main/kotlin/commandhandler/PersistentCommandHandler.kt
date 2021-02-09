package commandhandler

import functional.ErrorCode
import functional.Result
import functional.onEach
import statemachine.Application
import statemachine.StateId
import statemachine.Transition

class PersistentCommandHandler<C : Command>(
    val delegate: CommandHandler<C>,
    val saveTransition: (StateId, Transition) -> Unit
) : CommandHandler<C> {

    override fun invoke(command: C): Result<ErrorCode, Application> {
        return delegate.invoke(command)
            .onEach { application ->
                application.flattenTransitions()
                    .map {
                        saveTransition(application.new.id, it)
                    }
            }
    }
}