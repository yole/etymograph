import {useState} from "react";
import {allowEdit, deleteParadigm, fetchBackend, fetchPathsForAllGraphs} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";
import ParadigmForm from "@/forms/ParadigmForm";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `paradigm/${context.params.id}`)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("paradigms", (p) => ({id: p.id.toString()}))
}

export default function Paradigm(params) {
    const paradigm = params.loaderData
    const [editMode, setEditMode] = useState(false)

    const router = useRouter()
    const graph = router.query.graph

    function paradigmSubmitted() {
        setEditMode(false)
        router.replace(router.asPath)
    }

    function deleteParadigmClicked() {
        if (window.confirm("Delete this paradigm?")) {
            deleteParadigm(graph, paradigm.id)
                .then(() => router.push(`/${graph}/paradigms/` + paradigm.language))
        }
    }

    return <>
        <Breadcrumbs langId={paradigm.language} langName={paradigm.languageFullName}
                     steps={[{url: `/${graph}/paradigms/${paradigm.language}`, title: "Paradigms"}]}
                     title={paradigm.name}/>
        {!editMode && <>
            <p>POS: {paradigm.pos.join(", ")}</p>
            <table>
            <thead><tr>
                <td/>
                {paradigm.columns.map(c => <td>{c.title}</td>)}
            </tr></thead>
            <tbody>
            {paradigm.rowTitles.map((t, index) => <tr>
                <td>{t}</td>
                {paradigm.columns.map(col => <td>
                    {col.cells[index].alternativeRuleIds.map((alt, ai) => <>
                        {ai > 0 && <>&nbsp;|&nbsp;</>}
                        <Link href={`/${graph}/rule/${alt}`}>{col.cells[index].alternativeRuleSummaries[ai]}</Link>
                    </>)}
                </td>)}
            </tr>)}
            </tbody>
            </table>
        </>}
        {editMode && <>
            <ParadigmForm
                updateId={paradigm.id}
                lang={paradigm.language}
                defaultValues={{
                    name: paradigm.name,
                    pos: paradigm.pos.join(","),
                    text: paradigm.editableText
                }}
                submitted={paradigmSubmitted}
                cancelled={() => setEditMode(false)}
            />
        </>}
        {allowEdit() && <>
            {!editMode && <><button onClick={() => setEditMode(true)}>Edit</button>{' '}</>}
            <button onClick={() => deleteParadigmClicked()}>Delete</button>
        </>}
    </>
}
