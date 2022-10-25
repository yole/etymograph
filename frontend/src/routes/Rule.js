import {useLoaderData} from "react-router";
import {useState} from "react";

export async function loader({params}) {
    return fetch(`http://localhost:8080/rule/${params.lang}/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Rule() {
    const rule = useLoaderData()
    const [editMode, setEditMode] = useState(false)

    return <>
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
            <textarea rows="10" cols="50">{rule.prettyText}</textarea>
            <br/>
        </>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
    </>
}
