import {useState} from "react";
import {addLink, addWord, updateWord} from "../api";

export default function WordForm(props) {
    const [newWordText, setNewWordText] = useState(props.predefWord !== undefined ? props.predefWord : "")
    const [newWordGloss, setNewWordGloss] = useState(props.initialGloss !== undefined ? props.initialGloss : "")
    const [newWordPos, setNewWordPos] = useState(props.initialPos !== undefined ? props.initialPos : "")
    const [newWordSource, setNewWordSource] = useState(props.initialSource !== undefined ? props.initialSource : "")
    const [newWordLanguage, setNewWordLanguage] = useState(props.language || "")

    function handleFormSubmit(e) {
        if (props.updateId !== undefined) {
            updateWord(props.updateId, newWordGloss, newWordPos, newWordSource)
                .then(r => r.json())
                .then(r => props.submitted(r))
        }
        else {
            addWord(newWordLanguage, newWordText, newWordGloss, newWordPos, newWordSource)
                .then(r => r.json())
                .then(r => {
                    if (props.derivedWord) {
                        addLink(props.derivedWord.id, r.id, '>').then(() => props.submitted(r))
                    }
                    else if (props.baseWord) {
                        addLink(r.id, props.baseWord.id, '>').then(() => props.submitted(r))
                    }
                    else if (props.compoundWord) {
                        addLink(props.compoundWord.id, r.id, '+').then(() => props.submitted(r))
                    }
                    else {
                        props.submitted(r)
                    }
                })
            setNewWordText("")
            setNewWordGloss("")
        }

        e.preventDefault()
    }

    return <form onSubmit={handleFormSubmit}>
        <table>
            <tbody>
            {props.language === undefined && <tr>
                <td><label>Language:</label></td>
                <td><input type="text" value={newWordLanguage} onChange={e => setNewWordLanguage(e.target.value)}
                           id="word-input"/></td>
            </tr>}
            {props.updateId === undefined && <tr>
                <td><label>Text:</label></td>
                <td><input type="text" value={newWordText} onChange={e => setNewWordText(e.target.value)}
                           id="word-text" readOnly={props.predefWord !== undefined}/></td>
            </tr>}
            <tr>
                <td><label htmlFor="word-pos">POS:</label></td>
                <td><input type="text" value={newWordPos} onChange={e => setNewWordPos(e.target.value)}
                           id="word-pos"/></td>
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
