import {callApiAndRefresh, fetchBackend, fetchPathsForAllGraphs, reapplyRuleSequence} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import {DerivationViewModel, ReapplyResultViewModel, SequenceDerivationsViewModel} from "@/models";
import WordLink from "@/components/WordLink";
import {useRouter} from "next/router";
import Link from "next/link";
import {WordLinkComponent} from "@/pages/[graph]/word/[lang]/[...text]";
import {useState} from "react";

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
    showDerivations: boolean,
}) {
    const router = useRouter()
    const graph = router.query.graph as string
    const haveNotes = params.derivations.find(d => d.derivation.notes)

    return <>
        <table className="tableWithBorders">
            <thead>
            <tr>
                <td>{params.derivations[0].baseWord.language}</td>
                <td>{params.derivations[0].derivation.word.language}</td>
                {params.showExpectedWord && <td>Expected</td>}
                <td>Gloss</td>
                {params.showDerivations && <td>Derivation</td>}
                {haveNotes && <td>Notes</td>}
            </tr>
            </thead>
        <tbody>

        {params.derivations.map(derivation =>

        <tr>
            <td><WordLink word={derivation.baseWord}/></td>
            <td><WordLink word={derivation.derivation.word}/></td>
            {params.showExpectedWord && <td>{derivation.expectedWord}</td>}
            <td>{derivation.derivation.word.gloss}</td>
            {params.showDerivations && <td>
                {derivation.baseWord.text}
                {derivation.derivation.ruleIds.map((ruleId, index) => <>
                    {' '}<Link href={`/${graph}/rule/${ruleId}`} title={derivation.derivation.ruleNames[index]}>{'>'}</Link>{' '}
                    {derivation.derivation.ruleResults[index]}
                </>)}
            </td>}
            {haveNotes && <td>
                {derivation.derivation.notes}
            </td>}
        </tr>)}
        </tbody></table>
    </>
}

export default function RuleSequence(params) {
    const ruleSequence = params.loaderData as SequenceDerivationsViewModel
    const router = useRouter()
    const graph = router.query.graph as string
    const [reapplyResult, setReapplyResult] = useState(null)
    const [posFilter, setPosFilter] = useState(null)
    const [showDerivations, setShowDerivations] = useState(true)

    async function reapplySequenceClicked() {
        const result = await reapplyRuleSequence(graph, ruleSequence.sequence.id)
        if (result.status === 200) {
            const jr = await result.json()
            setReapplyResult(jr as ReapplyResultViewModel)
        }
    }

    const posSet = new Set(ruleSequence.derivations.map(d => d.pos))
    const pos = []
    for (const p of posSet.values()) {
        pos.push(p)
    }

    const derivations = posFilter == null
        ? ruleSequence.derivations
        : ruleSequence.derivations.filter(d =>
            (posFilter === "?") ? (d.pos == null) : (d.pos == posFilter))

    const consistent = derivations.filter(derivation =>
        derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord == null)
    const singlePhonemeInconsistent = derivations.filter(derivation =>
        derivation.derivation.suggestedSequences.length == 0 && derivation.expectedWord != null && derivation.singlePhonemeDifference != null)

    const spiMap = Map.groupBy(singlePhonemeInconsistent,
        (d) => d.singlePhonemeDifference)
    const spiGroups = []
    spiMap.forEach((v, k) => spiGroups.push({title: k, group: v}))
    spiGroups.sort((e1, e2) => e2.group.length - e1.group.length)

    const inconsistent = derivations.filter(derivation =>
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

        {reapplyResult && <div>
            Consistent: {reapplyResult.consistent};
            {reapplyResult.becomesConsistent.length &&
                <> becomes consistent: {reapplyResult.becomesConsistent.map(c => <><WordLink word={c}/>{' '}</>)}</>
            }
            {reapplyResult.becomesInconsistent.length &&
                <> becomes inconsistent: {reapplyResult.becomesInconsistent.map(c => <><WordLink word={c}/>{' '}</>)}</>
            }
            {' '}inconsistent: {reapplyResult.inconsistent}
        </div>}

        <div>
            POS:
            <select onChange={(e) => setPosFilter(e.target.value === "*" ? null : e.target.value)}>
                <option value="*">Any</option>
                {pos.map(p => <option value={p ?? "?"}>{p ?? "Unknown"}</option>)}
             </select>
        </div>
        <div>
            <input type="checkbox" defaultChecked={showDerivations} value={showDerivations} onChange={(e) => setShowDerivations(e.target.checked)} />
            Show derivations
        </div>

        <h3>Consistent Derivations</h3>
        <DerivationListComponent derivations={consistent} showExpectedWord={false} showDerivations={showDerivations}/>

        {spiGroups.map(group => <>
            <h3>{group.title}</h3>
            <DerivationListComponent derivations={group.group} showExpectedWord={true} showDerivations={showDerivations}/>
        </>)}

        {inconsistent.length > 0 && <>
            <h3>Inconsistent Derivations</h3>
            <DerivationListComponent derivations={inconsistent} showExpectedWord={true} showDerivations={showDerivations}/>
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
