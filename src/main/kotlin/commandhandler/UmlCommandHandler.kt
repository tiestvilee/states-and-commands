package commandhandler

import functional.ErrorCode
import functional.Result
import functional.onEach
import statemachine.Application
import statemachine.Transition

class UmlRenderer(private val umlTitle: String) {
    private val umlBody = StringBuilder()
    fun append(append: String) {
        umlBody.append(append)
    }

    fun toUml() = """@startuml
' skinparam responseMessageBelowArrow true
title $umlTitle
$umlBody
@enduml""".trimIndent()

    fun <T> group(group: String, function: () -> T): T {
        umlBody.append("group $group\n")
        return function().also {
            umlBody.append("end\n")
        }
    }

}

class UmlCommandHandler<C : Command, S : StateWithId, T : Transition>(
    val delegate: CommandHandler<C, S, T>,
    val umlRenderer: UmlRenderer
) : CommandHandler<C, S, T> {

    var step = 0

    override fun invoke(command: C): Result<ErrorCode, Application<S, T>> {
        step++
        umlRenderer.append(
            """
            |group ${command::class.simpleName}
            |""".trimMargin()
        )
        val result = delegate.invoke(command)
            .onEach { application ->
                umlRenderer.append(application.applied
                    .mapIndexed { index, it ->
                        val toStateName =
                            if (index + 1 < application.stateHistory.size) application.stateHistory[index + 1]::class.simpleName else application.state::class.simpleName
                        """  ${application.stateHistory[index]::class.simpleName} -> $toStateName : ${it::class.simpleName}
                            |""".trimMargin()
                    }
                    .joinToString("\n") +
                        "\n")
            }
        umlRenderer.append("end\n")
        return result
    }
}
