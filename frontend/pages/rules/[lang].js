import {useEffect, useState} from "react";
import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import RuleSequenceForm from "@/components/RuleSequenceForm";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`rules/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function RuleList(params) {
    const ruleList = params.loaderData
    const router = useRouter()
    const [sequenceEditId, setSequenceEditId] = useState(null)
    useEffect(() => { document.title = "Etymograph : Rules" })

    function sequenceSubmitted(toLang) {
        setSequenceEditId(null)
        router.push("/rules/" + toLang)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${router.query.lang}`}>{ruleList.toLangFullName}</Link> &gt; </small>
            Rules
        </h2>
        {ruleList.ruleGroups.map(g => <>
            <h2 key={g.groupName}>{g.groupName}</h2>
            {(g.sequenceId === null || sequenceEditId !== g.sequenceId) && <>
                <ul>
                    {g.rules.map(r => <li key={r.id}><Link href={`/rule/${r.id}`}>{r.name}</Link>{r.summaryText.length > 0 ? ": " + r.summaryText : ""}</li>)}
                </ul>
                {allowEdit() && g.sequenceId !== null &&
                    <button onClick={() => setSequenceEditId(g.sequenceId)}>Edit sequence</button>
                }
            </>}
            {g.sequenceId !== null && sequenceEditId === g.sequenceId && <>
                <RuleSequenceForm
                    updateId={g.sequenceId}
                    initialName={g.sequenceName}
                    initialFromLanguage={g.sequenceFromLang}
                    initialToLanguage={g.sequenceToLang}
                    initialRules={g.rules.map(r => r.name).join("\n")}
                    submitted={sequenceSubmitted}
                />
                <button onClick={() => setSequenceEditId(null)}>Cancel</button>
            </>}
        </>)}
        {allowEdit() && <p>
            <Link href="/rules/new">Add rule</Link> | <Link href="/rules/sequence/new">Add rule sequence</Link>
        </p>}
    </>
}
