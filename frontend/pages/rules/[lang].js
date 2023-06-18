import {useEffect} from "react";
import {allowEdit, fetchAllLanguagePaths, fetchBackend} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";

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
    useEffect(() => { document.title = "Etymograph : Rules" })

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
                <Link href={`/language/${router.query.lang}`}>{ruleList.toLangFullName}</Link> &gt; </small>
            Rules
        </h2>
        {ruleList.ruleGroups.map(g => <>
            <h2 key={g.groupName}>{g.groupName}</h2>
            <ul>
                {g.rules.map(r => <li key={r.id}><Link href={`/rule/${r.id}`}>{r.name}</Link>{r.summaryText.length > 0 ? ": " + r.summaryText : ""}</li>)}
            </ul>
        </>)}
        {allowEdit() && <Link href="/rules/new">Add rule</Link>}
    </>
}
