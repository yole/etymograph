import {useState} from "react";
import {addParadigm} from "@/api";
import {useRouter} from "next/router";

export default function ParadigmEditor() {
    const [name, setName] = useState("")
    const [pos, setPos] = useState("")
    const [editableText, setEditableText] = useState("")
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const graph = router.query.graph

    function saveParadigm() {
        addParadigm(graph, name, router.query.lang, pos, editableText)
            .then(r => {
                if (r.status === 200) {
                    r.json().then(r => router.push(`/${graph}/paradigm/${r.id}`))
                }
                else {
                    r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save paradigm"))
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
            <td><label>POS:</label></td>
            <td><input type="text" value={pos} onChange={(e) => setPos(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <textarea rows="10" cols="50" value={editableText} onChange={e => setEditableText(e.target.value)}/>
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        <button onClick={saveParadigm}>Save</button>
    </>
}
