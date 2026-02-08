import {useState} from "react";
import {useRouter} from "next/router";
import Link from "next/link";
import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import {RuleListViewModel, RuleSequenceViewModel, RuleShortViewModel} from "@/models";
import Breadcrumbs from "@/components/Breadcrumbs";
import LanguageNavBar from "@/components/LanguageNavBar";
import RuleSequenceForm from "@/forms/RuleSequenceForm";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}/phono`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

function RuleListView(params: {list: RuleListViewModel}) {
    const router = useRouter()
    const graph = router.query.graph as string
    const lang = router.query.lang as string
    const [sequenceEditId, setSequenceEditId] = useState(null)

    function sequenceSubmitted(data: RuleSequenceViewModel) {
        setSequenceEditId(null)
        router.push(`/${graph}/rules/${data.toLang}/phono`)
    }

    function editableRuleName(r: RuleShortViewModel) {
        return r.name + (r.alternative ? ("|" + r.alternative.name) : "") +
            (r.dispreferred ? "??" : (r.optional ? "?" : ""));
    }

    const ruleList = params.list
    return <>{ruleList.ruleGroups.map(g => <>
        <h2 key={g.groupName}>{g.groupName}</h2>
        {g.sequenceId !== null && <p>
            <Link href={`/${graph}/rules/sequence/${g.sequenceId}`}>Derivations</Link>{' | '}
            <Link href={`/${graph}/rules/sequence/report/${g.sequenceId}`}>Report</Link>
        </p>}
        {(g.sequenceId === null || sequenceEditId !== g.sequenceId) && <>
            <table className="tableWithBorders"><tbody>
            {g.rules.map(r => <tr key={r.id}>
                <td>
                    {!r.name.startsWith("sequence: ") && <Link href={`/${graph}/rule/${r.id}`}>{r.name}</Link>}
                    {r.name.startsWith("sequence: ") && r.name}
                    {r.alternative && <> | <Link href={`/${graph}/rule/${r.alternative.id}`}>{r.alternative.name}</Link></>}
                    {r.dispreferred && " (dispreferred)"}
                    {r.optional && !r.dispreferred && " (optional)"}
                </td>
                <td>
                    {r.summaryText.length > 0 ? r.summaryText : ""}
                </td>
            </tr>)}
            </tbody></table>
            {allowEdit() && g.sequenceId !== null &&
                <><button onClick={() => setSequenceEditId(g.sequenceId)}>Edit Sequence</button>{' '}
                    <button onClick={() => router.push(`/${graph}/rules/${lang}/new?addToSequence=${g.sequenceId}&fromLang=${g.sequenceFromLang}`)}>Add Rule</button>{' '}</>
            }
        </>}
        {g.sequenceId !== null && sequenceEditId === g.sequenceId && <>
            <RuleSequenceForm
                updateId={g.sequenceId}
                defaultValues={{
                    name: g.sequenceName,
                    fromLang: g.sequenceFromLang,
                    toLang: g.sequenceToLang,
                    ruleNames: g.rules.map(r => editableRuleName(r)).join("\n")
                }}
                submitted={sequenceSubmitted}
                cancelled={() => setSequenceEditId(null)}
            />
        </>}
    </>)}
    </>
}


export default function MorphoRuleList(params) {
    const ruleList = params.loaderData as RuleListViewModel
    const router = useRouter()
    const lang = router.query.lang as string
    const graph = router.query.graph as string
    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Historical Phonology"/>
        <LanguageNavBar langId={lang}/>
        <p/>
        <RuleListView list={ruleList}/>
        {allowEdit() && <p>
            <button className="uiButton" onClick={() => router.push(`/${graph}/rules/${lang}/new?type=phono`)}>Add Rule</button>
            {' '}
            <button className="uiButton" onClick={() => router.push(`/${graph}/rules/sequence/new`)}>Add Rule Sequence</button>
        </p>}
    </>
}
