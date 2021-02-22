package commandhandler

import functional.ErrorCode
import functional.Result
import statemachine.Application
import statemachine.State
import statemachine.Transition

interface Command

interface CommandHandler<C : Command, S : State, T : Transition> {
    fun invoke(command: C): Result<ErrorCode, Application<S, T>>
}
