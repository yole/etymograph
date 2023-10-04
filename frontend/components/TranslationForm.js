import {addTranslation, editTranslation} from "@/api";
import {useState} from "react";

export default function TranslationForm(props) {
    const [translationText, setTranslationText] = useState(props.initialText !== undefined ? props.initialText : "")
    const [translationSource, setTranslationSource] = useState(props.initialSource !== undefined ? props.initialSource : "")

    function submitTranslation() {
        if (props.updateId !== undefined) {
            editTranslation(props.updateId, translationText, translationSource).then(() => props.submitted())
        }
        else {
            addTranslation(props.corpusTextId, translationText, translationSource).then(() => {
                props.submitted()
            })
        }
    }

    return <>
        <p/>
        <textarea rows="10" cols="50" value={translationText} onChange={e => setTranslationText(e.target.value)}/>
        <table><tbody>
        <tr>
            <td><label htmlFor="source">Source:</label></td>
            <td><input type="text" id="source" value={translationSource} onChange={(e) => setTranslationSource(e.target.value)}/></td>
        </tr>
        </tbody></table>
        <button onClick={() => submitTranslation()}>Submit</button>
    </>
}
