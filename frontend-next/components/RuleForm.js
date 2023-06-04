import {useState} from "react";
import {addRule} from "@/api";

export default function RuleForm(props) {
    const [name, setName] = useState("")
    const [fromLanguage, setFromLanguage] = useState("")
    const [toLanguage, setToLanguage] = useState("")
    const [addedCategories, setAddedCategories] = useState("")
    const [replacedCategories, setReplacedCategories] = useState("")
    const [fromPOS, setFromPOS] = useState("")
    const [toPOS, setToPOS] = useState("")
    const [source, setSource] = useState("")
    const [notes, setNotes] = useState("")
    const [editableText, setEditableText] = useState("")
    const [errorText, setErrorText] = useState("")

    function saveRule() {
        addRule(name, fromLanguage, toLanguage, addedCategories, replacedCategories, fromPOS, toPOS, editableText, source, notes)
            .then(r => {
                if (r.status === 200)
                    r.json().then(r => props.submitted(r.id))
                else {
                    r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save rule"))
                }
            })
    }

    return <>
        <table><tbody>
        <tr>
            <td><label>Name:</label></td>
            <td><input type="text" value={name} onChange={(e) => setName(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>From language:</label></td>
            <td><input type="text" value={fromLanguage} onChange={(e) => setFromLanguage(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>To language:</label></td>
            <td><input type="text" value={toLanguage} onChange={(e) => setToLanguage(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>Added categories:</label></td>
            <td><input type="text" value={addedCategories} onChange={(e) => setAddedCategories(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>Replaced categories:</label></td>
            <td><input type="text" value={replacedCategories} onChange={(e) => setReplacedCategories(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>From POS</label></td>
            <td><input type="text" value={fromPOS} onChange={(e) => setFromPOS(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>To POS:</label></td>
            <td><input type="text" value={toPOS} onChange={(e) => setToPOS(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>Source:</label></td>
            <td><input type="text" value={source} onChange={(e) => setSource(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <textarea rows="10" cols="50" value={editableText} onChange={e => setEditableText(e.target.value)}/>
        <br/>
        <h3>Notes</h3>
        <textarea rows="5" cols="50" value={notes} onChange={e => setNotes(e.target.value)}/>
        <br/>
        <button onClick={saveRule}>Save</button>
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}