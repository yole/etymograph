import {useEffect, useState} from "react";
import {fetchBackend, updateParadigm} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";

export async function getStaticProps(context) {
    return fetchBackend(`paradigm/${context.params.id}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`paradigms`)
    const paths = props.loaderData.map(paradigm => ({params: {id: paradigm.id.toString()}}))
    return {paths, fallback: false}
}

export default function Paradigm(params) {
    const paradigm = params.loaderData
    const [editMode, setEditMode] = useState(false)
    const [editableText, setEditableText] = useState(paradigm.editableText)
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : " + paradigm.language + " " + paradigm.name + " Paradigm" })

    function saveParadigm() {
        updateParadigm(paradigm.id, paradigm.name, paradigm.pos, editableText)
            .then(() => router.replace(router.asPath))
        setEditMode(false)
    }

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${paradigm.language}`}>{paradigm.languageFullName}</Link> {'> '}
            <Link href={`/paradigms/${paradigm.language}`}>Paradigms</Link>{' > '}</small>
            {paradigm.name}</h2>
        <p>POS: {paradigm.pos}</p>
        {!editMode && <table>
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
                        <Link href={`/rule/${alt}`}>{col.cells[index].alternativeRuleSummaries[ai]}</Link>
                    </>)}
                </td>)}
            </tr>)}
            </tbody>
        </table>}
        {editMode && <>
            <textarea rows="10" cols="80" value={editableText} onChange={(e) => setEditableText(e.target.value)}/>
            <br/>
            <button onClick={() => saveParadigm()}>Save</button>
        </>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
    </>
}
