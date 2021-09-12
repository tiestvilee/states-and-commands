package example.eligibilityworkflow

import org.junit.Test
import statemachine.puml

class EligibilityWorkflowTest {
    @Test
    fun `outputs dot graph`() {
        println(eligibilityWorkflow.puml())
    }

}