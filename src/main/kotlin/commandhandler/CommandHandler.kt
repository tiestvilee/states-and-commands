package commandhandler

import functional.ErrorCode
import functional.Result
import statemachine.Application

interface Command

interface CommandHandler<C : Command> {
    fun invoke(command: C): Result<ErrorCode, Application>
}
