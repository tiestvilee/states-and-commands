package commandhandler

import functional.ErrorCode
import functional.Result
import functional.map
import statemachine.Application
import statemachine.StateId
import statemachine.Transition

class PersistentCommandHandler<C : Command>(
    val delegate: CommandHandler<C>,
    val saveTransition: (StateId, Transition) -> Unit
) : CommandHandler<C> {

    override fun invoke(command: C): Result<ErrorCode, Application> {
        return delegate.invoke(command)
            .map { application ->
                application.flattenTransitions()
                    .map {
                        saveTransition(application.new.id, it)
                    }

                application
            }
    }
}