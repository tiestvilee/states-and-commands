package example.eligibilityworkflow

import statemachine.State
import statemachine.StateMachine
import statemachine.Transition
import java.math.BigDecimal
import java.net.URI

/* Workflow */
val eligibilityWorkflow = StateMachine<EligibilityWorkflowState, EligibilityWorkflowEvent>()
    .defineStateTransition { initialState: NotFound, event: CreatedEligibilityWorkflow ->
        WaitingForEligibilityCriteria(
            event.eligibilityWorkflowId,
            event.articleInfo,
            event.journalPublishingModel,
            event.mirageLink
        )
    }
    .defineStateTransition { initialState: NotFound, event: CreatedSubscriptionWorkflow ->
        MustBeSubscription(
            event.eligibilityWorkflowId,
            event.articleInfo,
            event.journalPublishingModel,
            false,
            event.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForEligibilityCriteria, event: EligibilityCriteriaCollected ->
        WaitingForEligibilityResult(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            event.allEligibilityMatches,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForEligibilityResult, event: NoDealFound ->
        WaitingForPublishingModelChoice(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            false,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForEligibilityResult, event: DealFound ->
        WaitingForAuthorConfirmation(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.eligibilityMatches,
            event.contract,
            state.journalPublishingModel,
            state.mirageLink,
            event.contractPrice
        )
    }
    .defineStateTransition { state: WaitingForPublishingModelChoice, event: AuthorChoseSubscription ->
        DecidedAsSubscription(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.rejectedByInstitution,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForAuthorConfirmation, event: AuthorChoseSubscription ->
        MustBeSubscription(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            false,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForPublishingModelChoice, event: AuthorChoseOA ->
        DecidedAsOAOutsideDeal(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.rejectedByInstitution,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForAuthorConfirmation, event: ApprovalRequested ->
        WaitingForInstitutionApproval(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.mirageLink,
            state.contract
        )
    }
    .defineStateTransition { state: WaitingForInstitutionApproval, event: ApprovedByInstitution ->
        WaitingToNotifyApprovalDecision(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.mirageLink,
            state.contract
        )
    }
    .defineStateTransition { state: WaitingToNotifyApprovalDecision, event: AuthorNotifiedDecision ->
        DecidedAsOAInDeal(event.eligibilityWorkflowId)
    }
    .defineStateTransition { state: WaitingToNotifyRejectionDecision, event: AuthorNotifiedDecision ->
        WaitingForPublishingModelChoice(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            true,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForAuthorConfirmation, event: ResetWorkflow ->
        WaitingForEligibilityCriteria(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForPublishingModelChoice, event: ResetWorkflow ->
        WaitingForEligibilityCriteria(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.mirageLink
        )
    }
    .defineStateTransition { state: DecidedAsSubscription, event: PublishingModelChoiceReset ->
        WaitingForPublishingModelChoice(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.rejectedByInstitution,
            state.mirageLink
        )
    }
    .defineStateTransition { state: DecidedAsOAOutsideDeal, event: PublishingModelChoiceReset ->
        WaitingForPublishingModelChoice(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.rejectedByInstitution,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForInstitutionApproval, event: RejectedByInstitution ->
        WaitingToNotifyRejectionDecision(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.mirageLink,
            state.contract
        )
    }
//    .defineStateTransition { state: WaitingForEligibilityCriteria, event: Aborted ->
//        HasBeenAborted(event.eligibilityWorkflowId)
//    }
// all the other aborts
    .defineStateTransition { state: WaitingForEligibilityCriteria, event: ExternalEligibilityRequested ->
        WaitingForExternalEligibilityDecision(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForExternalEligibilityDecision, event: ExternalEligibilityOutsideDeal ->
        DecidedAsOAOutsideDeal(
            event.eligibilityWorkflowId,
            state.articleInfo,
            state.journalPublishingModel,
            false,
            state.mirageLink
        )
    }
    .defineStateTransition { state: WaitingForExternalEligibilityDecision, event: ExternalEligibilityInDeal ->
        DecidedAsOAInDeal(event.eligibilityWorkflowId)
    }
    .forAllStatesAddTransition(listOf(HasBeenAborted::class)) { state: EligibilityWorkflowState, event: Aborted ->
        HasBeenAborted(event.eligibilityWorkflowId)
    }


/* States */
sealed class EligibilityWorkflowState : State {
    abstract val eligibilityId: EligibilityWorkflowId
}

data class NotFound(override val eligibilityId: EligibilityWorkflowId) : EligibilityWorkflowState()

data class WaitingForEligibilityCriteria(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class WaitingForEligibilityResult internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val eligibilityMatches: AllEligibilityMatches,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class WaitingForPublishingModelChoice internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val rejectedByInstitution: Boolean,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class WaitingForAuthorConfirmation internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val eligibilityMatches: AllEligibilityMatches,
    val contract: Contract,
    val journalPublishingModel: JournalPublishingModel,
    val mirageLink: URI,
    val contractPriceInformation: ContractPriceInformation?
) : EligibilityWorkflowState()

data class WaitingForInstitutionApproval internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val mirageLink: URI,
    val contract: Contract
) : EligibilityWorkflowState()

data class WaitingToNotifyRejectionDecision internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val mirageLink: URI,
    val contract: Contract
) : EligibilityWorkflowState()

data class WaitingToNotifyApprovalDecision internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val mirageLink: URI,
    val contract: Contract
) : EligibilityWorkflowState()

data class DecidedAsSubscription internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val rejectedByInstitution: Boolean,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class MustBeSubscription internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val rejectedByInstitution: Boolean,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class DecidedAsOAInDeal internal constructor(
    override val eligibilityId: EligibilityWorkflowId
) : EligibilityWorkflowState()

data class DecidedAsOAOutsideDeal internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val rejectedByInstitution: Boolean,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class WaitingForExternalEligibilityDecision internal constructor(
    override val eligibilityId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val mirageLink: URI
) : EligibilityWorkflowState()

data class HasBeenAborted(
    override val eligibilityId: EligibilityWorkflowId
) : EligibilityWorkflowState()

/* Transitions */
sealed class EligibilityWorkflowEvent : Transition {
    abstract val eligibilityWorkflowId: EligibilityWorkflowId
}

data class CreatedEligibilityWorkflow(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val eligibilityMatches: AllEligibilityMatches,
    val mirageLink: URI
) : EligibilityWorkflowEvent()

data class CreatedSubscriptionWorkflow(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val articleInfo: ArticleInfo,
    val journalPublishingModel: JournalPublishingModel,
    val eligibilityMatches: AllEligibilityMatches,
    val mirageLink: URI
) : EligibilityWorkflowEvent()

data class AuthorChoseSubscription(override val eligibilityWorkflowId: EligibilityWorkflowId) :
    EligibilityWorkflowEvent()

data class AuthorChoseOA(override val eligibilityWorkflowId: EligibilityWorkflowId) : EligibilityWorkflowEvent()
data class EligibilityCriteriaCollected(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val allEligibilityMatches: AllEligibilityMatches
) : EligibilityWorkflowEvent()

data class NoDealFound(override val eligibilityWorkflowId: EligibilityWorkflowId) : EligibilityWorkflowEvent()
data class DealFound(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val contract: Contract,
    val priceType: ContractPricingType,
    val contractPrice: ContractPriceInformation?
) : EligibilityWorkflowEvent() // TODO remove priceType and use contractPrice instead, make contractPrice mandatory

data class ApprovedByInstitution(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val approvingInstitution: BPID
) :
    EligibilityWorkflowEvent()

data class RejectedByInstitution(override val eligibilityWorkflowId: EligibilityWorkflowId) : EligibilityWorkflowEvent()
data class ApprovalRequested(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val approvalId: ApprovalId,
    val confirmInstitutionChoice: ConfirmInstitutionChoice
) : EligibilityWorkflowEvent()

data class AuthorNotifiedDecision(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val transmissionId: TransmissionId
) :
    EligibilityWorkflowEvent()

data class ResetWorkflow(override val eligibilityWorkflowId: EligibilityWorkflowId) : EligibilityWorkflowEvent()
data class PublishingModelChoiceReset(override val eligibilityWorkflowId: EligibilityWorkflowId) :
    EligibilityWorkflowEvent()

data class Aborted(override val eligibilityWorkflowId: EligibilityWorkflowId) : EligibilityWorkflowEvent()
data class ExternalEligibilityRequested(override val eligibilityWorkflowId: EligibilityWorkflowId) :
    EligibilityWorkflowEvent()

data class ExternalEligibilityOutsideDeal(override val eligibilityWorkflowId: EligibilityWorkflowId) :
    EligibilityWorkflowEvent()

data class ExternalEligibilityInDeal(
    override val eligibilityWorkflowId: EligibilityWorkflowId,
    val contractNumber: ContractNumber,
    val confirmInstitutionChoice: ConfirmInstitutionChoice,
    val publicationFunding: PublicationFunding,
    val bpId: BPID,
    val contractPricingType: ContractPricingType
) : EligibilityWorkflowEvent()

/* Domain Objects */
data class EligibilityWorkflowId(val raw: String)
data class UserId(val raw: String)

data class ArticleInfo(val doi: DOI)
data class DOI(val raw: String)
data class BPID(val raw: String)
data class ApprovalId(val raw: String)
data class ContractNumber(val raw: String)
data class TransmissionId(val raw: String)
enum class JournalPublishingModel { Subscription, FullyOpenAccess, Hybrid }
data class PublicationFunding(
    val funderName: String,
    val fundingNote: String
)

data class Contract(
    val bpid: BPID,
    val welcomeMessage: String?,
    val optOutAllowed: Boolean,
    val matchReason: MatchReason,
    val dealName: String?,
)

enum class MatchReason { AuthorSelection, Email, IpAddress }
data class AllEligibilityMatches(
    val ipEligibilityMatch: IpEligibilityMatch,
    val emailEligibilityMatch: EmailEligibilityMatch,
    val selectionEligibilityMatch: SelectionEligibilityMatch
)

data class IpEligibilityMatch(val ip: IpAddress, val bpid: BPID?)

data class EmailEligibilityMatch(val emailDomain: EmailDomain, val bpid: BPID?)

data class SelectionEligibilityMatch(val selection: InstitutionChoice, val bpid: BPID?)

data class IpAddress(val raw: String)
data class EmailDomain(val raw: String)
data class InstitutionChoice(val raw: String)


sealed class ContractPriceInformation

data class ArticleListPrice(
    val articlePrice: CalculatedPrice,
    val institutionalPrice: CalculatedPrice,
    val authorPrice: CalculatedPrice
) : ContractPriceInformation()

object ArticleContractedPrice : ContractPriceInformation()

data class CalculatedPrice(val amount: BigDecimal, val adjustments: List<Adjustment>)

data class Adjustment(val description: AdjustmentSummary, val amount: BigDecimal)

enum class AdjustmentSummary {
    ListPrice, InstitutionShare, AuthorShare, Discount;
}

enum class ContractPricingType {
    ContractPrice, ListPrice
}

sealed class ConfirmInstitutionChoice
object NotSharedPayment : ConfirmInstitutionChoice()
object SharedPartialCoverage : ConfirmInstitutionChoice()
data class SharedFullCoverage(val reason: String) : ConfirmInstitutionChoice()
