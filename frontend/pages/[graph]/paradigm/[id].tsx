import {deleteParadigm, fetchBackend, fetchPathsForAllGraphs} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";
import ParadigmForm from "@/forms/ParadigmForm";
import RuleLink from "@/components/RuleLink";
import EtymographFormView, {View} from "@/components/EtymographFormView";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `paradigm/${context.params.id}`, true)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("paradigms", (p) => ({id: p.id.toString()}))
}

export default function Paradigm(params) {
    const paradigm = params.loaderData

    const router = useRouter()
    const graph = router.query.graph as string

    function deleteParadigmClicked() {
        if (window.confirm("Delete this paradigm?")) {
            deleteParadigm(graph, paradigm.id)
                .then(() => router.push(`/${graph}/rules/${paradigm.language}/morpho`))
        }
    }

    function ruleTitle(cell, index) {
         const summary = cell.alternativeRuleSummaries[index]
         if (summary.length > 0) {
             return summary
         }
         return cell.alternativeRuleNames[index]
    }

    return <>
        <Breadcrumbs langId={paradigm.language} langName={paradigm.languageFullName}
                     steps={[{url: `/${graph}/rules/${paradigm.language}/morpho`, title: "Morphology"}]}
                     title={paradigm.name}/>

        <EtymographFormView buttons={[{text: "Delete", callback: () => deleteParadigmClicked()}]}>
            <View>
                <p>POS: {paradigm.pos.join(", ")}</p>
                {paradigm.preRule && <p>Pre rule: <RuleLink rule={paradigm.preRule}/></p>}
                {paradigm.postRule && <p>Post rule: <RuleLink rule={paradigm.postRule}/></p>}
                <p/>
                <table className="tableWithBorders">
                    <thead><tr>
                        <td/>
                        {paradigm.columns.map(c => <td>{c.title}</td>)}
                    </tr></thead>
                    <tbody>
                    {paradigm.rowTitles.map((t, index) => <tr>
                        <td>{t}</td>
                        {paradigm.columns.map(col => <td>
                            {col.cells[index].alternativeRuleIds.filter((alt) => alt != null).map((alt, ai) => <>
                                {ai > 0 && <>&nbsp;|&nbsp;</>}
                                <Link href={`/${graph}/rule/${alt}`}>{ruleTitle(col.cells[index], ai)}</Link>
                            </>)}
                        </td>)}
                    </tr>)}
                    </tbody>
                </table>
            </View>
            <ParadigmForm
                updateId={paradigm.id}
                lang={paradigm.language}
                defaultValues={{
                    name: paradigm.name,
                    pos: paradigm.pos.join(","),
                    text: paradigm.editableText,
                    preRuleName: paradigm.preRule?.name,
                    postRuleName: paradigm.postRule?.name
                }}
            />
        </EtymographFormView>
    </>
}
