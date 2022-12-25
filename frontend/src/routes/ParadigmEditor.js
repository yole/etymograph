import {useState} from "react";
import {useNavigate, useParams} from "react-router";
import {addParadigm} from "../api";

export default function ParadigmEditor() {
    const [name, setName] = useState("")
    const [pos, setPos] = useState("")
    const [editableText, setEditableText] = useState("")
    const params = useParams()
    const navigate = useNavigate()

    function saveParadigm() {
        addParadigm(name, params.lang, pos, editableText)
            .then(r => r.json())
            .then(r => navigate("/paradigm/" + r.id))
    }

    return <>
        <table><tbody>
        <tr>
            <td><label>Name:</label></td>
            <td><input type="text" value={name} onChange={(e) => setName(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>POS:</label></td>
            <td><input type="text" value={pos} onChange={(e) => setPos(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <textarea rows="10" cols="50" value={editableText} onChange={e => setEditableText(e.target.value)}/>
        <br/>
        <button onClick={saveParadigm}>Save</button>
    </>
}
