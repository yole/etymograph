import {useLoaderData, useRevalidator} from "react-router";
import {useEffect, useState} from "react";
import {updateRule} from "../api";
import {Link} from "react-router-dom";
import {WordLink} from "./Word";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rule/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Rule() {
    const rule = useLoaderData()
    const [editMode, setEditMode] = useState(false)
    const [addedCategories, setAddedCategories] = useState(rule.addedCategories)
    const [replacedCategories, setReplacedCategories] = useState(rule.replacedCategories)
    const [source, setSource] = useState(rule.source)
    const [editableText, setEditableText] = useState(rule.editableText)
    const [notes, setNotes] = useState(rule.notes)
    const revalidator = useRevalidator()
    const [errorText, setErrorText] = useState("")
    useEffect(() => { document.title = "Etymograph : Rule " + rule.name })

    function saveRule() {
        updateRule(rule.id, rule.name, rule.fromLang, rule.toLang, addedCategories, replacedCategories, editableText, source, notes)
            .then(r => {
                if (r.status === 200) {
                    setErrorText("")
                    revalidator.revalidate()
                }
                else {
                    r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save rule"))
                }
            })
        setEditMode(false)
    }

    return <>
        <h2><small>
            <Link to={`/`}>Etymograph</Link> >{' '}
            <Link to={`/language/${rule.toLang}`}>{rule.toLangFullName}</Link> >{' '}
            <Link to={`/rules/${rule.toLang}`}>Rules</Link> > </small>
            {rule.name}</h2>
        {rule.fromLang !== rule.toLang && <p>From {rule.fromLangFullName} to {rule.toLangFullName}</p>}
        {rule.paradigmId !== null && <p>Paradigm: <Link to={`/paradigm/${rule.paradigmId}`}>{rule.paradigmName}</Link></p>}
        {!editMode && <>
            {rule.addedCategories && <p>Added categories: {rule.addedCategories}</p>}
            {rule.replacedCategories && <p>Replaced categories: {rule.replacedCategories}</p>}
            {rule.source != null && <div className="source">Source: {rule.source.startsWith("http") ? <a href={rule.source}>{rule.source}</a> : rule.source}</div>}
            <p/>
            <ul>
                {rule.preInstructions.map(r => <li>{r}</li>)}
            </ul>
            {rule.branches.map(b => <>
                {b.conditions !== "" && <div>{b.conditions}:</div>}
                <ul>
                    {b.instructions.map(i => <li>{i}</li>)}
                </ul>
            </>)}
            {rule.notes != null && <>
                <h3>Notes</h3>
                <p>{rule.notes}</p>
            </>}
        </>}
        {editMode && <>
            <table><tbody>
            <tr>
                <td><label>Added categories:</label></td>
                <td><input type="text" value={addedCategories} onChange={(e) => setAddedCategories(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Replaced categories:</label></td>
                <td><input type="text" value={replacedCategories} onChange={(e) => setReplacedCategories(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Source:</label></td>
                <td><input type="text" value={source} onChange={(e) => setSource(e.target.value)}/></td>
            </tr>
            </tbody></table>
            <textarea rows="10" cols="50" value={editableText} onChange={(e) => setEditableText(e.target.value)}/>
            <br/>
            <h3>Notes</h3>
            <textarea rows="5" cols="50" value={notes} onChange={(e) => setNotes(e.target.value)}/>
            <br/>
            <button onClick={() => saveRule()}>Save</button>&nbsp;
        </>}

        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {rule.examples.length > 0 && <>
            <h3>Examples</h3>
            <ul>
                {rule.examples.map(ex => <li>
                    <WordLink word={ex.toWord}/>
                    {ex.toWord.gloss && `" ${ex.toWord.gloss}"`}
                    &nbsp;->&nbsp;
                    <WordLink word={ex.fromWord}/>
                    {ex.allRules.length > 1 && " (" + ex.allRules.join(", ") + ")"}
                    {ex.expectedWord !== null && ex.expectedWord !== ex.fromWord && " [expected: " + ex.expectedWord + "]"}
                </li>)}
            </ul>
        </>}
    </>
}
