import {callApiAndRefresh, fetchBackend, fetchPathsForAllGraphs, reapplyRuleSequence} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import {DerivationViewModel, SequenceDerivationsViewModel} from "@/models";
import WordLink from "@/components/WordLink";
import {useRouter} from "next/router";
import Link from "next/link";
import {WordLinkComponent} from "@/pages/[graph]/word/[lang]/[...text]";

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
    derivations: DerivationViewModel[],
    showExpectedWord: boolean,
}) {
    const router = useRouter()
    const graph = router.query.graph as string

    return <>
        <table className="tableWithBorders"><tbody>

        {params.derivations.map(derivation =>

        <tr>
            <td><WordLink word={derivation.baseWord}/></td>
            <td><WordLink word={derivation.derivation.word}/></td>
            {params.showExpectedWord && <td>{derivation.expectedWord}</td>}
            <td>{derivation.derivation.word.gloss}</td>
            <td>
                {derivation.baseWord.text}
                {derivation.derivation.ruleIds.map((ruleId, index) => <>
                    {' '}<Link href={`/${graph}/rule/${ruleId}`} title={derivation.derivation.ruleNames[index]}>{'>'}</Link>{' '}
                    {derivation.derivation.ruleResults[index]}
                </>)}
            </td>
        </tr>)}
        </tbody></table>
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

    const consistent = ruleSequence.derivations.filter(derivation =>
        derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord == null)
    const singlePhonemeInconsistent = ruleSequence.derivations.filter(derivation =>
        derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord != null && derivation.singlePhonemeDifference != null)
    const inconsistent = ruleSequence.derivations.filter(derivation =>
        derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord != null && derivation.singlePhonemeDifference == null)
    const total = consistent.length + inconsistent.length + singlePhonemeInconsistent.length
    const candidates = ruleSequence.derivations.filter(derivation =>
        derivation.derivation.suggestedSequences.length > 0)
    const consistentRate = total == 0 ? 0 : consistent.length / total
    const singlePhonemeRate = total == 0 ? 0 : singlePhonemeInconsistent.length / total

    return <>
        <Breadcrumbs
            title={ruleSequence.sequence.name}
            langId={ruleSequence.sequence.toLang}
            steps={[{title: "Rules", url: `/${graph}/rules/${ruleSequence.sequence.toLang}`}]}
        />

        <button type="button" onClick={reapplySequenceClicked}>Reapply</button>

        <h3>Consistent Derivations</h3>
        <DerivationListComponent derivations={consistent} showExpectedWord={false}/>
        {singlePhonemeInconsistent.length > 0 && <>
            <h3>Single-phoneme Inconsistent Derivations</h3>
            <DerivationListComponent derivations={singlePhonemeInconsistent} showExpectedWord={true}/>
        </>}
        {inconsistent.length > 0 && <>
            <h3>Inconsistent Derivations</h3>
            <DerivationListComponent derivations={inconsistent} showExpectedWord={true}/>
        </>}
        <h3>Statistics</h3>
        Consistent: {consistent.length} ({Math.round(consistentRate * 100)}%);
        single-phoneme inconsistent: {singlePhonemeInconsistent.length} ({Math.round(singlePhonemeRate * 100)}%);
        inconsistent: {inconsistent.length};
        total: {total}

        {candidates.length > 0 && <>
            <h3>Candidates</h3>
            {candidates.map(derivation =>
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
        </>}
    </>
}
