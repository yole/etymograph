import {useState} from "react";
import {addRule} from "../api";
import {useNavigate} from "react-router";

export default function RuleEditor() {
    const [name, setName] = useState("")
    const [fromLanguage, setFromLanguage] = useState("")
    const [toLanguage, setToLanguage] = useState("")
    const [addedCategories, setAddedCategories] = useState("")
    const [source, setSource] = useState("")
    const [editableText, setEditableText] = useState("")
    const navigate = useNavigate()

    function saveRule() {
        addRule(name, fromLanguage, toLanguage, addedCategories, editableText, source)
            .then(r => r.json())
            .then(r => navigate("/rule/" + r.id))
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
                <td><label>Source:</label></td>
                <td><input type="text" value={source} onChange={(e) => setSource(e.target.value)}/></td>
            </tr>
        </tbody></table>
        <textarea rows="10" cols="50" value={editableText} onChange={e => setEditableText(e.target.value)}/>
        <br/>
        <button onClick={saveRule}>Save</button>
    </>
}
