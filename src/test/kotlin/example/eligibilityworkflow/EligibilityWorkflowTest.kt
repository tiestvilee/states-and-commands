package example.eligibilityworkflow

import org.junit.Test
import statemachine.puml
import java.io.File

class EligibilityWorkflowTest {
    @Test
    fun `outputs dot graph`() {
        File("src/test/kotlin/example/eligibilityworkflow/states.puml")
            .writeText(eligibilityWorkflow.puml())
    }

}