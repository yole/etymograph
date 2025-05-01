// noinspection JSUnusedGlobalSymbols
import {fetchBackend, fetchPathsForAllGraphs} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import {SequenceReportViewModel} from "@/models";
import {useRouter} from "next/router";
import RichText from "@/components/RichText";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rule/sequence/${context.params.id}/rules`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("rules/sequences", (r) => ({id: r.id.toString()}))
}

export default function RuleSequenceReport(params) {
    const ruleSequence = params.loaderData as SequenceReportViewModel
    const router = useRouter()
    const graph = router.query.graph as string
    return <>
        <Breadcrumbs
            title={ruleSequence.name}
            langId={ruleSequence.toLang}
            steps={[{title: "Rules", url: `/${graph}/rules/${ruleSequence.toLang}`}]}
        />
        {ruleSequence.rules.map(r => <>
            <h2>{r.ruleName} {r.optional && " (optional)"}</h2>
            <span className="source">{r.ruleSource}</span>
            {r.preInstructions.map(i => <div>{'- '}<RichText richText={i}/></div>)}
            {r.ruleIsSPE && r.branches[0].instructions.map(i => <div><RichText richText={i}/></div>)}
            {!r.ruleIsSPE && r.branches.map(b => <>
                <div><RichText richText={b.conditions}/>:</div>
                <ul>
                    {b.instructions.map(i => <li><RichText richText={i}/></li>)}
                </ul>
            </>)}
            {r.postInstructions.map(i => <div>{'= '}<RichText richText={i}/></div>)}
        </>)}
    </>
}
