import {useState} from "react";
import {addCorpusText, updateCorpusText} from "@/api";

export default function CorpusTextForm(props) {
    const [title, setTitle] = useState(props.initialTitle !== undefined ? props.initialTitle : "")
    const [text, setText] = useState(props.initialText !== undefined ? props.initialText : "")
    const [source, setSource] = useState(props.initialSource !== undefined ? props.initialSource : "")

    function saveCorpusText() {
        if (props.updateId !== undefined) {
            updateCorpusText(props.updateId, title, text, source)
                .then(props.submitted())
        }
        else {
            addCorpusText(props.lang, title, text, source)
                .then(r => r.json())
                .then(r => props.submitted(r))
        }
    }

    return <>
        <table><tbody>
        <tr>
            <td><label htmlFor="title">Title:</label></td>
            <td><input type="text" id="title" value={title} onChange={(e) => setTitle(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <textarea rows="10" cols="50" value={text} onChange={e => setText(e.target.value)}/>
        <table><tbody>
        <tr>
            <td><label htmlFor="source">Source:</label></td>
            <td><input type="text" id="source" value={source} onChange={(e) => setSource(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <br/>
        <button onClick={() => saveCorpusText()}>Save</button>
    </>
}
