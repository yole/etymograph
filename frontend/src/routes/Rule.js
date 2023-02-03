import {useLoaderData, useRevalidator} from "react-router";
import {useEffect, useState} from "react";
import {updateRule} from "../api";
import {Link} from "react-router-dom";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rule/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Rule() {
    const rule = useLoaderData()
    const [editMode, setEditMode] = useState(false)
    const [editableText, setEditableText] = useState(rule.editableText)
    const revalidator = useRevalidator()
    useEffect(() => { document.title = "Etymograph : Rule " + rule.name })

    function saveRule() {
        updateRule(rule.id, rule.name, rule.fromLang, rule.toLang, editableText)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
        <h2><small><Link to="/rules">Rules</Link> > </small>{rule.name}</h2>
        <p>From {rule.fromLang} to {rule.toLang}</p>
        {rule.addedCategories && <p>Added categories: {rule.addedCategories}</p>}
        {rule.replacedCategories && <p>Replaced categories: {rule.replacedCategories}</p>}
        {!editMode && rule.branches.map(b => <>
            {(rule.branches.length > 1 || rule.fromLang != rule.toLang) && <div>{b.conditions}:</div>}
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
        {rule.examples.length > 0 && <>
            <h3>Examples</h3>
            <ul>
                {rule.examples.map(ex => <li>
                    <Link to={`/word/${rule.toLang}/${ex.toWord}`}>{ex.toWord}</Link>
                    &nbsp;->&nbsp;
                    <Link to={`/word/${rule.fromLang}/${ex.fromWord}`}>{ex.fromWord}</Link>
                    {ex.allRules.length > 1 && " (" + ex.allRules.join(", ") + ")"}
                    {ex.expectedWord !== ex.fromWord && " [expected: " + ex.expectedWord + "]"}
                </li>)}
            </ul>
        </>}
    </>
}
