package commandhandler

import functional.ErrorCode
import functional.Result
import functional.onEach
import statemachine.Application
import statemachine.Transition

class UmlCommandHandler<C : Command, S : StateWithId, T : Transition>(
    val delegate: CommandHandler<C, S, T>,
    umlTitle: String
) : CommandHandler<C, S, T> {

    var uml = """@startuml
' skinparam responseMessageBelowArrow true
title $umlTitle
"""
    var step = 0

    override fun invoke(command: C): Result<ErrorCode, Application<S, T>> {
        step++
        uml += """
            |group ${command::class.simpleName}
            |""".trimMargin()
        val result = delegate.invoke(command)
            .onEach { application ->
                uml += application.applied
                    .mapIndexed { index, it ->
                        """  ${application.stateHistory[index]::class.simpleName} -> ${application.state::class.simpleName} : ${it::class.simpleName}"
                            |""".trimMargin()
                    }
                    .joinToString("\n") +
                        "\n"
            }
        uml += "end\n"
        return result
    }

    fun uml(): String {
        return uml + """@enduml"""
    }
}
