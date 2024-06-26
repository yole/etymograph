import {useState} from "react";
import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import RuleSequenceForm from "@/forms/RuleSequenceForm";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function RuleList(params) {
    const ruleList = params.loaderData
    const router = useRouter()
    const graph = router.query.graph
    const [sequenceEditId, setSequenceEditId] = useState(null)

    function sequenceSubmitted(data) {
        setSequenceEditId(null)
        router.push(`/${graph}/rules/${data.toLang}`)
    }

    return <>
        <Breadcrumbs langId={router.query.lang} langName={ruleList.toLangFullName} title="Rules"/>
        {ruleList.ruleGroups.map(g => <>
            <h2 key={g.groupName}>{g.groupName}</h2>
            {(g.sequenceId === null || sequenceEditId !== g.sequenceId) && <>
                <table className="tableWithBorders"><tbody>
                    {g.rules.map(r => <tr key={r.id}>
                        <td>
                            {!r.name.startsWith("sequence: ") && <Link href={`/${graph}/rule/${r.id}`}>{r.name}</Link>}
                            {r.name.startsWith("sequence: ") && r.name}
                            {r.optional && " (optional)"}
                        </td>
                        <td>
                            {r.summaryText.length > 0 ? r.summaryText : ""}
                        </td>
                    </tr>)}
                </tbody></table>
                {allowEdit() && g.sequenceId !== null &&
                    <button onClick={() => setSequenceEditId(g.sequenceId)}>Edit Sequence</button>
                }
            </>}
            {g.sequenceId !== null && sequenceEditId === g.sequenceId && <>
                <RuleSequenceForm
                    updateId={g.sequenceId}
                    defaultValues={{
                        name: g.sequenceName,
                        fromLang: g.sequenceFromLang,
                        toLang: g.sequenceToLang,
                        ruleNames: g.rules.map(r => r.name + (r.optional ? "?" : "")).join("\n")
                    }}
                    submitted={sequenceSubmitted}
                    cancelled={() => setSequenceEditId(null)}
                />
            </>}
        </>)}
        {allowEdit() && <p>
            <button onClick={() => router.push(`/${graph}/rules/${router.query.lang}/new`)}>Add Rule</button>
            {' '}
            <button onClick={() => router.push(`/${graph}/rules/sequence/new`)}>Add Rule Sequence</button>
        </p>}
    </>
}
