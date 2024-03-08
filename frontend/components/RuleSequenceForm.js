import {useState} from "react";
import {addRuleSequence, updateRuleSequence} from "@/api";

export default function RuleSequenceForm(props) {
    const [name, setName] = useState(props.initialName !== undefined ? props.initialName : "")
    const [fromLanguage, setFromLanguage] = useState(props.initialFromLanguage !== undefined ? props.initialFromLanguage : "")
    const [toLanguage, setToLanguage] = useState(props.initialToLanguage !== undefined ? props.initialToLanguage : "")
    const [rules, setRules] = useState(props.initialRules !== undefined ? props.initialRules : "")
    const [errorText, setErrorText] = useState("")

    function saveRuleSequence() {
        if (props.updateId !== undefined) {
            updateRuleSequence(props.updateId, name, fromLanguage, toLanguage, rules)
                .then(handleResponse)
        }
        else {
            addRuleSequence(name, fromLanguage, toLanguage, rules)
                .then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200)
            props.submitted(toLanguage)
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save rule sequence"))
        }
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
        </tbody></table>
        <h3>Rules</h3>
        <textarea rows="10" cols="50" value={rules} onChange={e => setRules(e.target.value)}/>
        <br/>
        <button onClick={saveRuleSequence}>Save</button>
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
