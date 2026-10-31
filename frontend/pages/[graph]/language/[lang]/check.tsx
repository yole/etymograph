import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import LanguageNavBar from "@/components/LanguageNavBar";
import RichText from "@/components/RichText";
import {ConsistencyViewModel} from "@/models";

export default function ConsistencyPage(props: {loaderData: ConsistencyViewModel}) {
    const report = props.loaderData
    const issueCount = report.issueGroups.reduce((count, group) => count + group.issues.length, 0)

    return <>
        <Breadcrumbs langId={report.language} langName={report.languageFullName} title="Data Consistency"/>
        <LanguageNavBar langId={report.language}/>
        {issueCount === 0
            ? <p>No consistency issues found.</p>
            : <p>{issueCount} consistency {issueCount === 1 ? "issue" : "issues"} found.</p>}
        {report.issueGroups.map(group => <section key={group.type}>
            <h3>{group.type} ({group.issues.length})</h3>
            <ul>
                {group.issues.map((issue, index) => <li key={index}><RichText richText={issue}/></li>)}
            </ul>
        </section>)}
    </>
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `consistency/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths
