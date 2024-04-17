import {useEffect, useState} from "react";
import {allowEdit, deleteParadigm, deleteRule, fetchBackend, fetchPathsForAllGraphs, updateParadigm} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import Breadcrumbs from "@/components/Breadcrumbs";

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
    const [editableText, setEditableText] = useState(paradigm.editableText)
    const [name, setName] = useState(paradigm.name)
    const [pos, setPos] = useState(paradigm.pos.join(", "))
    const [errorText, setErrorText] = useState("")

    const router = useRouter()
    const graph = router.query.graph

    function saveParadigm() {
        updateParadigm(graph, paradigm.id, name, pos, editableText)
            .then((r) => {
                if (r.status === 200) {
                    router.replace(router.asPath)
                } else {
                    r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save paradigm"))
                }
            })
        setEditMode(false)
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
            <table><tbody>
            <tr>
                <td><label>Name:</label></td>
                <td><input type="text" value={name} onChange={(e) => setName(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>POS:</label></td>
                <td><input type="text" value={pos} onChange={(e) => setPos(e.target.value)}/></td>
            </tr>
            </tbody></table>
            <textarea rows="10" cols="80" value={editableText} onChange={(e) => setEditableText(e.target.value)}/>
            <br/>
            <button onClick={() => saveParadigm()}>Save</button>
        </>}
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {allowEdit() && <>
            <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>{' '}
            <button onClick={() => deleteParadigmClicked()}>Delete</button>
        </>}
    </>
}
