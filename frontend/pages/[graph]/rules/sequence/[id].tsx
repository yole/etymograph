import {callApiAndRefresh, fetchBackend, fetchPathsForAllGraphs, reapplyRuleSequence} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import {DerivationViewModel, SequenceDerivationsViewModel} from "@/models";
import {WordLinkComponent} from "@/pages/[graph]/word/[lang]/[...text]";
import WordLink from "@/components/WordLink";
import {useRouter} from "next/router";

// noinspection JSUnusedGlobalSymbols
export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rule/sequence/${context.params.id}/derivations`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("rules/sequences", (r) => ({id: r.id.toString()}))
}

function DerivationListComponent(params: {
    derivations: DerivationViewModel[]
}) {
    return <>
        {params.derivations.map(derivation =>
        <div>
            <WordLink word={derivation.baseWord}/>{' > '}
            <WordLinkComponent
                baseWord={derivation.baseWord}
                linkWord={derivation.derivation}
                linkType={{typeId: '^', type: 'originates from'}}
                directionFrom={false}
                showSequence={false}
                linkClassName="derivationLinkChain"/>
            {derivation.expectedWord && " (expected: " + derivation.expectedWord + ")"}
        </div>)}
    </>
}

export default function RuleSequence(params) {
    const ruleSequence = params.loaderData as SequenceDerivationsViewModel
    const router = useRouter()
    const graph = router.query.graph as string

    async function reapplySequenceClicked() {
        callApiAndRefresh(
            () => reapplyRuleSequence(graph, ruleSequence.sequence.id),
            router,
            (message) => {}
        )
    }

    return <>
        <Breadcrumbs title={ruleSequence.sequence.name}></Breadcrumbs>

        <button type="button" onClick={reapplySequenceClicked}>Reapply</button>

        <h3>Consistent Derivations</h3>
        <DerivationListComponent derivations={ruleSequence.derivations.filter(derivation => derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord == null)} />
        {ruleSequence.derivations.find(d => d.derivation.suggestedSequences.length == 0 && d.expectedWord !== null) && <>
            <h3>Inconsistent Derivations</h3>
            <DerivationListComponent derivations={ruleSequence.derivations.filter(derivation => derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord !== null)} />
        </>}
        {ruleSequence.derivations.find(d => d.derivation.suggestedSequences.length > 0) && <>
            <h3>Candidates</h3>
            <DerivationListComponent derivations={ruleSequence.derivations.filter(derivation => derivation.derivation.suggestedSequences.length > 0)} />
        </>}
    </>
}
