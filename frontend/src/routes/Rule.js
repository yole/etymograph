import {useLoaderData, useRevalidator} from "react-router";
import {useState} from "react";
import {updateRule} from "../api";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rule/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Rule() {
    const rule = useLoaderData()
    const [editMode, setEditMode] = useState(false)
    const [prettyText, setPrettyText] = useState(rule.prettyText)
    const revalidator = useRevalidator()

    function saveRule() {
        updateRule(rule.id, rule.fromLang, rule.toLang, prettyText)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
        <h3>{rule.addedCategories}</h3>
        {!editMode && rule.branches.map(b => <>
            <div>When:</div>
            <ul>
                {b.conditions.map(c => <li>{c}</li>)}
            </ul>
            <div>Then:</div>
            <ul>
                {b.instructions.map(i => <li>{i}</li>)}
            </ul>
        </>)}
        {editMode && <>
            <textarea rows="10" cols="50" value={prettyText} onChange={(e) => setPrettyText(e.target.value)}/>
            <br/>
            <button onClick={() => saveRule()}>Save</button>
        </>}
        {rule.source != null && <div className="source">Source: {rule.source}</div>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
    </>
}
