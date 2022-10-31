import {useState} from "react";
import {addLink, addWord} from "../api";

export default function WordForm(props) {
    const [newWordText, setNewWordText] = useState("")
    const [newWordGloss, setNewWordGloss] = useState("")
    const [newWordSource, setNewWordSource] = useState("")

    function handleFormSubmit(e) {
        addWord(props.language, newWordText, newWordGloss, newWordSource)
            .then(r => r.json())
            .then(r => {
                if (props.derivedWord) {
                    addLink(props.derivedWord.id, r.id, '>').then(() => props.submitted())
                }
                else if (props.baseWord) {
                    addLink(r.id, props.baseWord.id, '>').then(() => props.submitted())
                }
                else {
                    props.submitted()
                }
            })
        setNewWordText("")
        setNewWordGloss("")

        e.preventDefault()
    }

    return <form onSubmit={handleFormSubmit}>
        <table>
            <tbody>
            <tr>
                <td><label>Text:</label></td>
                <td><input type="text" value={newWordText} onChange={e => setNewWordText(e.target.value)}
                           id="word-text"/></td>
            </tr>
            <tr>
                <td><label>Gloss:</label></td>
                <td><input type="text" value={newWordGloss} onChange={e => setNewWordGloss(e.target.value)}
                           id="word-gloss"/></td>
            </tr>
            <tr>
                <td><label>Source:</label></td>
                <td><input type="text" value={newWordSource} onChange={e => setNewWordSource(e.target.value)}
                           id="word-input"/></td>
            </tr>
            </tbody>
        </table>
        <button type="submit">Submit</button>
    </form>
}
