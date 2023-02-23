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
    const [addedCategories, setAddedCategories] = useState(rule.addedCategories)
    const [replacedCategories, setReplacedCategories] = useState(rule.replacedCategories)
    const [source, setSource] = useState(rule.source)
    const [editableText, setEditableText] = useState(rule.editableText)
    const [notes, setNotes] = useState(rule.notes)
    const revalidator = useRevalidator()
    useEffect(() => { document.title = "Etymograph : Rule " + rule.name })

    function saveRule() {
        updateRule(rule.id, rule.name, rule.fromLang, rule.toLang, addedCategories, replacedCategories, editableText, source, notes)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
        <h2><small>
            <Link to={`/`}>Etymograph</Link> >{' '}
            <Link to={`/language/${rule.toLang}`}>{rule.toLangFullName}</Link> >{' '}
            <Link to={`/rules/${rule.toLang}`}>Rules</Link> > </small>
            {rule.name}</h2>
        {rule.fromLang !== rule.toLang && <p>From {rule.fromLangFullName} to {rule.toLangFullName}</p>}
        {!editMode && <>
            {rule.addedCategories && <p>Added categories: {rule.addedCategories}</p>}
            {rule.replacedCategories && <p>Replaced categories: {rule.replacedCategories}</p>}
            {rule.source != null && <div className="source">Source: {rule.source.startsWith("http") ? <a href={rule.source}>{rule.source}</a> : rule.source}</div>}
            <p/>
            {rule.branches.map(b => <>
                {(rule.branches.length > 1 || rule.fromLang !== rule.toLang) && <div>{b.conditions}:</div>}
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
        {rule.examples.length > 0 && <>
            <h3>Examples</h3>
            <ul>
                {rule.examples.map(ex => <li>
                    <Link to={`/word/${rule.toLang}/${ex.toWord}`}>{ex.toWord}</Link>
                    {ex.toWordGloss && `" ${ex.toWordGloss}"`}
                    &nbsp;->&nbsp;
                    <Link to={`/word/${rule.fromLang}/${ex.fromWord}`}>{ex.fromWord}</Link>
                    {ex.allRules.length > 1 && " (" + ex.allRules.join(", ") + ")"}
                    {ex.expectedWord !== null && ex.expectedWord !== ex.fromWord && " [expected: " + ex.expectedWord + "]"}
                </li>)}
            </ul>
        </>}
    </>
}
