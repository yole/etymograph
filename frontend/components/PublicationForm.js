import {useState} from "react";
import {addPublication, updatePublication} from "@/api";

export default function PublicationForm(props) {
    const [name, setName] = useState(props.initialName !== undefined ? props.initialName : "")
    const [refId, setRefId] = useState(props.initialRefId !== undefined ? props.initialRefId : "")
    const [errorText, setErrorText] = useState("")

    function savePublication() {
        if (props.updateId !== undefined) {
            updatePublication(props.updateId, name, refId).then(handleResponse)
        }
        else {
            addPublication(name, refId).then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200)
            r.json().then(r => props.submitted(r.id))
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save publication"))
        }
    }

    return <>
        <table><tbody>
        <tr>
            <td><label>Name:</label></td>
            <td><input type="text" value={name} onChange={(e) => setName(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>Reference ID</label></td>
            <td><input type="text" value={refId} onChange={(e) => setRefId(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <button onClick={savePublication}>Save</button>
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
