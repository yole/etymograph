import {useState} from "react";
import {addLink, addToCompound, addWord, createCompound, updateWord} from "@/api";

export default function WordForm(props) {
    const [newWordText, setNewWordText] = useState(props.initialText !== undefined ? props.initialText : "")
    const [newWordGloss, setNewWordGloss] = useState(props.initialGloss !== undefined ? props.initialGloss : "")
    const [newWordFullGloss, setNewWordFullGloss] = useState(props.initialFullGloss !== undefined ? props.initialFullGloss : "")
    const [newWordPosClasses, setNewWordPosClasses] = useState(props.initialPosClasses !== undefined ? props.initialPosClasses : "")
    const [newWordSource, setNewWordSource] = useState(props.initialSource !== undefined ? props.initialSource : "")
    const [newWordLanguage, setNewWordLanguage] = useState(props.language || "")
    const [newWordNotes, setNewWordNotes] = useState(props.initialNotes !== undefined ? props.initialNotes : "")
    const [newWordLinkRuleNames, setNewWordLinkRuleNames] = useState("")
    const [newWordLinkSource, setNewWordLinkSource] = useState("")
    const isAddingLink = props.linkType !== undefined

    function clearFields() {
        setNewWordText("")
        setNewWordGloss("")
        setNewWordFullGloss("")
        setNewWordNotes("")
        setNewWordLinkSource("")
    }

    function handleFormSubmit(e) {
        if (props.updateId !== undefined) {
            updateWord(props.updateId, newWordText, newWordGloss, newWordFullGloss, newWordPosClasses, newWordSource, newWordNotes)
                .then(r => r.json().then(jr => props.submitted(r.status, jr)))
        }
        else {
            addWord(newWordLanguage, newWordText, newWordGloss, newWordFullGloss, newWordPosClasses, newWordSource, newWordNotes)
                .then(r => {
                    r.json().then(jr => {
                        let onFulfilled = lr => {
                            if (lr.status === 200) {
                                props.submitted(r.status, jr)
                                clearFields()
                            }
                            else
                                lr.json().then(lr => props.submitted(r.status, jr, lr))
                        }

                        if (isAddingLink) {
                            let fromId, toId
                            if (props.reverseLink === true) {
                                [fromId, toId] = [props.linkTarget.id, jr.id]
                            } else {
                                [fromId, toId] = [jr.id, props.linkTarget.id]
                            }
                            addLink(fromId, toId, props.linkType, newWordLinkRuleNames, newWordLinkSource)
                                .then(onFulfilled)
                        } else if (props.newCompound === true) {
                            createCompound(props.linkTarget.id, jr.id, newWordLinkSource).then(onFulfilled)
                        } else if (props.addToCompound !== undefined) {
                            addToCompound(props.addToCompound, jr.id).then(onFulfilled)
                        } else {
                            if (r.status === 200) {
                                clearFields()
                            }
                            props.submitted(r.status, jr)
                        }
                    })
                })
        }

        e.preventDefault()
    }

    return <form onSubmit={handleFormSubmit}>
        <table>
            <tbody>
            {props.languageReadOnly !== true && <tr>
                <td><label>Language:</label></td>
                <td><input type="text" value={newWordLanguage} onChange={e => setNewWordLanguage(e.target.value)}
                           id="word-input"/></td>
            </tr>}
            <tr>
                <td><label htmlFor="word-text">Text:</label></td>
                <td><input type="text" value={newWordText} onChange={e => setNewWordText(e.target.value)}
                           id="word-text" readOnly={props.textReadOnly === true}/></td>
            </tr>
            <tr>
                <td><label htmlFor="word-pos">POS/classes:</label></td>
                <td><input type="text" value={newWordPosClasses} onChange={e => setNewWordPosClasses(e.target.value)}
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
            {props.linkType === '>' && <tr>
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
            {(props.newCompound === true || isAddingLink) && <tr>
                <td><label htmlFor="link-source"></label>{isAddingLink ? "Link source:" : "Compound source:"}</td>
                <td><input type="text" value={newWordLinkSource} onChange={e => setNewWordLinkSource(e.target.value)}
                           id="link-source"/></td>
            </tr>}
            </tbody>
        </table>
        <button type="submit">Submit</button>
    </form>
}
