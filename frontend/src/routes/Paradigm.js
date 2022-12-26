import {useLoaderData, useRevalidator} from "react-router";
import {useState} from "react";
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

    function saveParadigm() {
        updateParadigm(paradigm.id, paradigm.name, paradigm.pos, editableText)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
    <h3>{paradigm.name}</h3>
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
                    {col.cells[index].ruleSummaries.map((c, i) => <Link to={`/rule/${col.cells[index].ruleIds[i]}`}>{c}</Link>)}
                </td>)}
            </tr>)}
        </tbody>
    </table>}
    {editMode && <>
        <textarea rows="10" cols="50" value={editableText} onChange={(e) => setEditableText(e.target.value)}/>
        <br/>
        <button onClick={() => saveParadigm()}>Save</button>
    </>}
    <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
    </>
}
