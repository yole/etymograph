import {useState} from "react";
import {useRouter} from "next/router";
import Link from "next/link";
import {Modal} from "@mantine/core";
import {fetchAllLanguagePaths, fetchBackend, generateParadigm, useAllowEditGraph} from "@/api";
import {GenerateParadigmParameters, ParadigmViewModel, RuleListViewModel} from "@/models";
import Breadcrumbs from "@/components/Breadcrumbs";
import LanguageNavBar from "@/components/LanguageNavBar";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import SourceInput from "@/components/SourceInput";
import PosSelect from "@/components/PosSelect";
import GrammaticalCategorySelect from "@/components/GrammaticalCategorySelect";

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
    const canEdit = useAllowEditGraph()
    const [generateParadigmOpened, setGenerateParadigmOpened] = useState(false)

    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Morphology"/>
        <LanguageNavBar langId={lang}/>
        <p/>
        <RuleListView list={ruleList}/>
        {canEdit && <>
            <p>
                <button className="uiButton" onClick={() => router.push(`/${graph}/rules/${lang}/new?type=morpho`)}>Add Rule</button>
                {' '}
                <button className="uiButton" onClick={() => router.push(`/${graph}/paradigms/${lang}/new`)}>Add Paradigm</button>
                {' '}
                <button className="uiButton" onClick={() => setGenerateParadigmOpened(true)}>Generate Paradigm</button>
            </p>
            <Modal opened={generateParadigmOpened} onClose={() => setGenerateParadigmOpened(false)} title="Generate Paradigm" size="lg">
                <EtymographForm<GenerateParadigmParameters, ParadigmViewModel>
                    create={(data) => generateParadigm(graph, lang, data)}
                    setEditMode={setGenerateParadigmOpened}
                    redirectOnCreate={(r) => `/${graph}/paradigm/${r.id}`}>

                    <table>
                        <tbody>
                        <FormRow label="Name" id="name"/>
                        <PosSelect label="POS" id="pos" language={lang} isMulti={true}/>
                        <FormRow label="Added Categories" id="addedCategories"/>
                        <FormRow label="Prefix" id="prefix"/>
                        <GrammaticalCategorySelect label="Rows" id="rows" language={lang} posProp="pos"/>
                        <GrammaticalCategorySelect label="Columns" id="columns" language={lang} posProp="pos"/>
                        <FormRow label="Endings" id="endings"/>
                        <SourceInput label="Source" id="source"/>
                        </tbody>
                    </table>
                </EtymographForm>
            </Modal>
        </>}
    </>
}
