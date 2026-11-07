import {useEffect, useState} from "react";
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
import ParadigmView from "@/components/ParadigmView";
import {Urls} from "@/components/Urls";

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}/morpho`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export function RuleListView(params: {list: RuleListViewModel, refresh: () => Promise<void>}) {
    const router = useRouter()
    const graph = router.query.graph as string

    const ruleList = params.list
    return <>{ruleList.ruleGroups.map(g => <section key={g.paradigm ? `paradigm-${g.paradigm.id}` : g.groupName}>
        <h2 id={g.paradigm ? `paradigm-${g.paradigm.id}` : undefined}>{g.groupName}</h2>
        {g.paradigm ? <ParadigmView paradigm={g.paradigm} refresh={params.refresh}/> : <table className="tableWithBorders"><tbody>
        {g.rules.map(r => <tr key={r.id}>
            <td>
                <Link href={`/${graph}/rule/${r.id}`}>{r.name}</Link>
            </td>
            <td>
                {r.summaryText.length > 0 ? r.summaryText : ""}
            </td>
        </tr>)}
        </tbody></table>}
    </section>)}
    </>
}

export default function MorphoRuleList(params) {
    const [ruleList, setRuleList] = useState<RuleListViewModel>(params.loaderData)
    useEffect(() => setRuleList(params.loaderData), [params.loaderData])
    const router = useRouter()
    const graph = router.query.graph as string
    const lang = router.query.lang as string
    const canEdit = useAllowEditGraph()
    const [generateParadigmOpened, setGenerateParadigmOpened] = useState(false)

    async function refreshRules() {
        const result = await fetchBackend(graph, `rules/${lang}/morpho`)
        setRuleList(result.props.loaderData)
    }

    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Morphology"/>
        <LanguageNavBar langId={lang}/>
        <p/>
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
                    submitted={async (paradigm) => {
                        await refreshRules()
                        setGenerateParadigmOpened(false)
                        await router.push(Urls.Rules.morpho(graph, paradigm.language, paradigm.id))
                    }}>

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
        <RuleListView list={ruleList} refresh={refreshRules}/>
    </>
}
