import {useLoaderData, useRevalidator} from "react-router";
import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {updateParadigm} from "../api";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}paradigm/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Paradigm() {
    const paradigm = useLoaderData()
    const [editMode, setEditMode] = useState(false)
    const [editableText, setEditableText] = useState(paradigm.editableText)
    const revalidator = useRevalidator()
    useEffect(() => { document.title = "Etymograph : " + paradigm.language + " " + paradigm.name + " Paradigm" })

    function saveParadigm() {
        updateParadigm(paradigm.id, paradigm.name, paradigm.pos, editableText)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
        <h2><small>
            <Link to={`/`}>Etymograph</Link> >{' '}
            <Link to={`/language/${paradigm.language}`}>{paradigm.languageFullName}</Link> >{' '}
            <Link to={`/paradigms/${paradigm.language}`}>Paradigms</Link> > </small>
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
                        <Link to={`/rule/${alt}`}>{col.cells[index].alternativeRuleSummaries[ai]}</Link>
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
