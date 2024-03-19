import {useState} from "react";
import {addPhoneme, updatePhoneme} from "@/api";

export default function PhonemeForm(props) {
    const [graphemes, setGraphemes] = useState(props.initialGraphemes !== undefined ? props.initialGraphemes : "")
    const [sound, setSound] = useState(props.initialSound !== undefined ? props.initialSound : "")
    const [classes, setClasses] = useState(props.initialClasses !== undefined ? props.initialClasses : "")
    const [source, setSource] = useState(props.initialSource !== undefined ? props.initialSource : "")
    const [errorText, setErrorText] = useState("")

    function savePhoneme() {
        if (props.updateId !== undefined) {
            updatePhoneme(props.updateId, graphemes, sound, classes, source).then(handleResponse)
        }
        else {
            addPhoneme(props.language, graphemes, sound, classes, source).then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200) {
            if (props.updateId !== undefined) {
                props.submitted()
            }
            else {
                r.json().then(r => props.submitted(r.id))
            }
        }
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save phoneme"))
        }
    }

    return <>
        <table>
            <tbody>
            <tr>
                <td><label>Graphemes:</label></td>
                <td><input type="text" value={graphemes} onChange={(e) => setGraphemes(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Sound:</label></td>
                <td><input type="text" value={sound} onChange={(e) => setSound(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Classes:</label></td>
                <td><input type="text" value={classes} onChange={(e) => setClasses(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Source:</label></td>
                <td><input type="text" value={source} onChange={(e) => setSource(e.target.value)}/></td>
            </tr>
            </tbody>
        </table>
        <button onClick={savePhoneme}>Save</button>
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
