import {useState} from "react";
import {addLink, addWord, updateWord} from "@/api";

export default function WordForm(props) {
    const [newWordText, setNewWordText] = useState(
        props.predefWord !== undefined ? props.predefWord : (props.initialWord !== undefined ? props.initialWord : "")
    )
    const [newWordGloss, setNewWordGloss] = useState(props.initialGloss !== undefined ? props.initialGloss : "")
    const [newWordFullGloss, setNewWordFullGloss] = useState(props.initialFullGloss !== undefined ? props.initialFullGloss : "")
    const [newWordPos, setNewWordPos] = useState(props.initialPos !== undefined ? props.initialPos : "")
    const [newWordSource, setNewWordSource] = useState(props.initialSource !== undefined ? props.initialSource : "")
    const [newWordLanguage, setNewWordLanguage] = useState(props.language || "")
    const [newWordNotes, setNewWordNotes] = useState(props.initialNotes !== undefined ? props.initialNotes : "")
    const [newWordLinkRuleNames, setNewWordLinkRuleNames] = useState("")
    const isAddingLink = props.derivedWord || props.baseWord || props.compoundWord || props.relatedWord

    function handleFormSubmit(e) {
        if (props.updateId !== undefined) {
            updateWord(props.updateId, newWordText, newWordGloss, newWordFullGloss, newWordPos, newWordSource, newWordNotes)
                .then(r => r.json())
                .then(r => props.submitted(r))
        }
        else {
            addWord(newWordLanguage, newWordText, newWordGloss, newWordFullGloss, newWordPos, newWordSource)
                .then(r => r.json())
                .then(r => {
                    if (isAddingLink) {
                        let fromId, toId, linkType
                        if (props.derivedWord) {
                            [fromId, toId, linkType] = [props.derivedWord.id, r.id, '>']
                        }
                        else if (props.baseWord) {
                            [fromId, toId, linkType] = [r.id, props.baseWord.id, '>']
                        }
                        else if (props.compoundWord) {
                            [fromId, toId, linkType] = [props.compoundWord.id, r.id, '+']
                        }
                        else {
                            [fromId, toId, linkType] = [props.relatedWord.id, r.id, '~']
                        }
                        addLink(fromId, toId, linkType, newWordLinkRuleNames)
                            .then(lr => {
                                if (lr.status === 200)
                                    props.submitted(r)
                                else
                                    lr.json().then(lr => props.submitted(r, lr))
                            })
                    }
                    else {
                        props.submitted(r)
                    }
                })
            setNewWordText("")
            setNewWordGloss("")
            setNewWordFullGloss("")
            setNewWordNotes("")
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
            <tr>
                <td><label htmlFor="word-text">Text:</label></td>
                <td><input type="text" value={newWordText} onChange={e => setNewWordText(e.target.value)}
                           id="word-text" readOnly={props.predefWord !== undefined}/></td>
            </tr>
            <tr>
                <td><label htmlFor="word-pos">POS:</label></td>
                <td><input type="text" value={newWordPos} onChange={e => setNewWordPos(e.target.value)}
                           id="word-pos"/></td>
            </tr>
            <tr>
                <td><label htmlFor="word-gloss">Gloss:</label></td>
                <td><input type="text" value={newWordGloss} onChange={e => setNewWordGloss(e.target.value)}
                           id="word-gloss"/></td>
            </tr>
            <tr>
                <td><label htmlFor="word-fullgloss">Full gloss:</label></td>
                <td><input type="text" value={newWordFullGloss} onChange={e => setNewWordFullGloss(e.target.value)}
                           id="word-fullgloss"/></td>
            </tr>
            {isAddingLink && <tr>
                <td><label htmlFor="word-link-rule-names">Link rule names:</label></td>
                <td><input type="text" value={newWordLinkRuleNames} onChange={e => setNewWordLinkRuleNames(e.target.value)}
                           id="word-link-rule-names"/></td>
            </tr>}
            <tr>
                <td><label>Source:</label></td>
                <td><input type="text" value={newWordSource} onChange={e => setNewWordSource(e.target.value)}
                           id="word-input"/></td>
            </tr>
            <tr>
                <td><label htmlFor="word-notes">Notes:</label></td>
                <td><textarea rows="3" cols="50" value={newWordNotes} onChange={e => setNewWordNotes(e.target.value)}
                           id="word-notes"/></td>
            </tr>
            </tbody>
        </table>
        <button type="submit">Submit</button>
    </form>
}
