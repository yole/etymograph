import {useLoaderData, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import {useState} from "react";
import {addWord} from "../api"

export async function loader({params}) {
    return fetch(`http://localhost:8080/dictionary/${params.lang}`, { headers: { 'Accept': 'application/json'} })
}


export default function Dictionary() {
    const dict = useLoaderData()
    const [newWordText, setNewWordText] = useState("")
    const [newWordGloss, setNewWordGloss] = useState("")
    const [newWordSource, setNewWordSource] = useState("")
    const revalidator = useRevalidator()

    function handleFormSubmit(e) {
        addWord(dict.language.shortName, newWordText, newWordGloss, newWordSource)
            .then(() => revalidator.revalidate())
        setNewWordText("")
        setNewWordGloss("")

        e.preventDefault()
    }

    return <>
        <h2>Dictionary for {dict.language.name}</h2>
        <h3>Add word</h3>
        <form onSubmit={handleFormSubmit}>
            <table><tbody>
                <tr>
                    <td><label>Text:</label></td>
                    <td><input type="text" value={newWordText} onChange={e => setNewWordText(e.target.value)} id="word-text"/></td>
                </tr>
                <tr>
                    <td><label>Gloss:</label></td>
                    <td><input type="text" value={newWordGloss} onChange={e => setNewWordGloss(e.target.value)} id="word-gloss"/></td>
                </tr>
                <tr>
                    <td><label>Source:</label></td>
                    <td><input type="text" value={newWordSource} onChange={e => setNewWordSource(e.target.value)} id="word-input"/></td>
                </tr>
            </tbody></table>
            <button type="submit">Submit</button>
        </form>

        <ul>
            {dict.words.map(w => <li><Link to={`/word/${dict.language.shortName}/${w.text}`}>{w.text}</Link> - {w.gloss}</li>)}
        </ul>
    </>
}
