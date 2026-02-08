import {useRouter} from "next/router";
import Link from "next/link";
import {allowEdit, fetchAllLanguagePaths, fetchBackend, generateParadigm} from "@/api";
import {GenerateParadigmParameters, ParadigmViewModel, RuleListViewModel} from "@/models";
import Breadcrumbs from "@/components/Breadcrumbs";
import LanguageNavBar from "@/components/LanguageNavBar";
import EtymographFormView from "@/components/EtymographFormView";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import PosSelect from "@/components/PosSelect";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}/morpho`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export function RuleListView(params: {list: RuleListViewModel}) {
    const router = useRouter()
    const graph = router.query.graph as string

    const ruleList = params.list
    return <>{ruleList.ruleGroups.map(g => <>
        <h2 key={g.groupName}>{g.groupName}</h2>
        {g.paradigmId !== null && <p><Link href={`/${graph}/paradigm/${g.paradigmId}`}>Paradigm</Link></p>}
        <table className="tableWithBorders"><tbody>
        {g.rules.map(r => <tr key={r.id}>
            <td>
                <Link href={`/${graph}/rule/${r.id}`}>{r.name}</Link>
            </td>
            <td>
                {r.summaryText.length > 0 ? r.summaryText : ""}
            </td>
        </tr>)}
        </tbody></table>
    </>)}
    </>
}

export default function MorphoRuleList(params) {
    const ruleList = params.loaderData as RuleListViewModel
    const router = useRouter()
    const graph = router.query.graph as string
    const lang = router.query.lang as string

    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Morphology"/>
        <LanguageNavBar langId={lang}/>
        <p/>
        <RuleListView list={ruleList}/>
        {allowEdit() && <p>
            <button className="uiButton" onClick={() => router.push(`/${graph}/rules/${lang}/new?type=morpho`)}>Add Rule</button>
            {' '}
            <button className="uiButton" onClick={() => router.push(`/${graph}/paradigms/${lang}/new`)}>Add Paradigm</button>
            {' '}
            <EtymographFormView editButtonTitle="Generate Paradigm">
                <EtymographForm<GenerateParadigmParameters, ParadigmViewModel>
                    create={(data) => generateParadigm(graph, lang, data)}
                    redirectOnCreate={(r) => `/${graph}/paradigm/${r.id}`}>

                    <table>
                        <tbody>
                        <FormRow label="Name" id="name"/>
                        <PosSelect label="POS" id="pos" language={lang}/>
                        <FormRow label="Added Categories" id="addedCategories"/>
                        <FormRow label="Prefix" id="prefix"/>
                        <FormRow label="Rows" id="rows"/>
                        <FormRow label="Columns" id="columns"/>
                        <FormRow label="Endings" id="endings"/>
                        </tbody>
                    </table>
                </EtymographForm>
            </EtymographFormView>
        </p>}
    </>
}
