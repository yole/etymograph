import {useLoaderData, useRevalidator} from "react-router";
import {useState} from "react";
import {updateRule} from "../api";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rule/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Rule() {
    const rule = useLoaderData()
    const [editMode, setEditMode] = useState(false)
    const [editableText, setEditableText] = useState(rule.editableText)
    const revalidator = useRevalidator()

    function saveRule() {
        updateRule(rule.id, rule.name, rule.fromLang, rule.toLang, editableText)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
        <h3>{rule.name}</h3>
        <p>Added categories: {rule.addedCategories}</p>
        {rule.replacedCategories && <p>Replaced categories: {rule.replacedCategories}</p>}
        {!editMode && rule.branches.map(b => <>
            <div>{b.conditions.length > 0 ? "When:" : "Otherwise:"}</div>
            <ul>
                {b.conditions.map(c => <li>{c}</li>)}
            </ul>
            {b.conditions.length > 0 && <div>Then:</div>}
            <ul>
                {b.instructions.map(i => <li>{i}</li>)}
            </ul>
        </>)}
        {editMode && <>
            <textarea rows="10" cols="50" value={editableText} onChange={(e) => setEditableText(e.target.value)}/>
            <br/>
            <button onClick={() => saveRule()}>Save</button>
        </>}
        {rule.source != null && <div className="source">Source: {rule.source}</div>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
    </>
}
