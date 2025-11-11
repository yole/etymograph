import {useState} from "react";
import {allowEdit, fetchAllLanguagePaths, fetchBackend, generateParadigm} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import RuleSequenceForm from "@/forms/RuleSequenceForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import EtymographFormView from "@/components/EtymographFormView";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import PosSelect from "@/components/PosSelect";
import {
    GenerateParadigmParameters,
    ParadigmViewModel,
    RuleListViewModel,
    RuleSequenceViewModel,
    RuleShortViewModel
} from "@/models";

// noinspection JSUnusedGlobalSymbols
export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `rules/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function RuleList(params) {
    const ruleList = params.loaderData as RuleListViewModel
    const router = useRouter()
    const graph = router.query.graph as string
    const lang = router.query.lang as string
    const [sequenceEditId, setSequenceEditId] = useState(null)

    function sequenceSubmitted(data: RuleSequenceViewModel) {
        setSequenceEditId(null)
        router.push(`/${graph}/rules/${data.toLang}`)
    }

    function editableRuleName(r: RuleShortViewModel) {
        return r.name + (r.alternative ? ("|" + r.alternative.name) : "") +
            (r.dispreferred ? "??" : (r.optional ? "?" : ""));
    }

    return <>
        <Breadcrumbs langId={lang} langName={ruleList.toLangFullName} title="Rules"/>
        {ruleList.ruleGroups.map(g => <>
            <h2 key={g.groupName}>{g.groupName}</h2>
            {g.paradigmId !== null && <p><Link href={`/${graph}/paradigm/${g.paradigmId}`}>Paradigm</Link></p>}
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
        {allowEdit() && <p>
            <button onClick={() => router.push(`/${graph}/rules/${lang}/new`)}>Add Rule</button>
            {' '}
            <button onClick={() => router.push(`/${graph}/rules/sequence/new`)}>Add Rule Sequence</button>
            {' '}
            <button onClick={() => router.push(`/${graph}/paradigms/${lang}/new`)}>Add Paradigm</button>
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
                        </tbody>
                    </table>
                </EtymographForm>
            </EtymographFormView>
        </p>}
    </>
}
