package example.eligibilityworkflow

import org.junit.Test
import statemachine.dot

class EligibilityWorkflowTest {
    @Test
    fun `outputs dot graph`() {
        println(eligibilityWorkflow.dot())
    }

}