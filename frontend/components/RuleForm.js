import {useState} from "react";
import {addRule, updateRule} from "@/api";

export default function RuleForm(props) {
    const [ruleType, setRuleType] = useState(props.initialType !== undefined ? props.initialType : "phono")
    const [name, setName] = useState(props.initialName !== undefined ? props.initialName : "")
    const [fromLanguage, setFromLanguage] = useState(props.initialFromLanguage !== undefined ? props.initialFromLanguage : "")
    const [toLanguage, setToLanguage] = useState(props.initialToLanguage !== undefined ? props.initialToLanguage : "")
    const [addedCategories, setAddedCategories] = useState(props.initialAddedCategories !== undefined ? props.initialAddedCategories : "")
    const [replacedCategories, setReplacedCategories] = useState(props.initialReplacedCategories !== undefined ? props.initialReplacedCategories : "")
    const [fromPOS, setFromPOS] = useState(props.initialFromPOS !== undefined ? props.initialFromPOS : "")
    const [toPOS, setToPOS] = useState(props.initialToPOS !== undefined ? props.initialToPOS : "")
    const [source, setSource] = useState(props.initialSource !== undefined ? props.initialSource : "")
    const [notes, setNotes] = useState(props.initialNotes !== undefined ? props.initialNotes : "")
    const [editableText, setEditableText] = useState(props.initialEditableText !== undefined ? props.initialEditableText : "")
    const [errorText, setErrorText] = useState("")

    function saveRule() {
        if (props.updateId !== undefined) {
            updateRule(props.updateId, name, fromLanguage, toLanguage, addedCategories, replacedCategories, fromPOS, toPOS, editableText, source, notes)
                .then(handleResponse)
        }
        else {
            addRule(name, fromLanguage, toLanguage, addedCategories, replacedCategories, fromPOS, toPOS, editableText, source, notes)
                .then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200)
            r.json().then(r => props.submitted(r.id))
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save rule"))
        }
    }

    return <>
        Rule type:{' '}
            <button className={ruleType === "morpho" ? "inlineButton inlineButtonActive " : "inlineButton"} onClick={() => setRuleType("morpho")}>Morphological</button>{' | '}
            <button className={ruleType === "phono" ? "inlineButton inlineButtonActive " : "inlineButton"} onClick={() => setRuleType("phono")}>Phonological</button>
        <hr/>
        <table><tbody>
        <tr>
            <td><label>Name:</label></td>
            <td><input type="text" value={name} onChange={(e) => setName(e.target.value)}/></td>
        </tr>
        {ruleType === "phono" && <tr>
            <td><label>From language:</label></td>
            <td><input type="text" value={fromLanguage} onChange={(e) => setFromLanguage(e.target.value)}/></td>
        </tr>}
        <tr>
            <td><label>{ruleType === "morpho" ? "Language:" : "To language:"}</label></td>
            <td><input type="text" value={toLanguage} onChange={(e) => setToLanguage(e.target.value)}/></td>
        </tr>
        {ruleType === "morpho" && <>
            <tr>
                <td><label>Added category values:</label></td>
                <td><input type="text" value={addedCategories} onChange={(e) => setAddedCategories(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Replaced category values:</label></td>
                <td><input type="text" value={replacedCategories}
                           onChange={(e) => setReplacedCategories(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>From POS</label></td>
                <td><input type="text" value={fromPOS} onChange={(e) => setFromPOS(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>To POS:</label></td>
                <td><input type="text" value={toPOS} onChange={(e) => setToPOS(e.target.value)}/></td>
            </tr>
        </>}
        <tr>
            <td><label>Source:</label></td>
            <td><input type="text" value={source} onChange={(e) => setSource(e.target.value)}/></td>
        </tr>
        </tbody>
        </table>
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