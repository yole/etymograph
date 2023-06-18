import {useState} from "react";
import {addParadigm} from "@/api";
import {useRouter} from "next/router";

export default function ParadigmEditor() {
    const [name, setName] = useState("")
    const [pos, setPos] = useState("")
    const [editableText, setEditableText] = useState("")
    const router = useRouter()

    function saveParadigm() {
        addParadigm(name, router.query.lang, pos, editableText)
            .then(r => r.json())
            .then(r => router.push("/paradigm/" + r.id))
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
