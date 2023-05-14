import {useRouter} from "next/router";
import {addCorpusText} from "@/api";
import {useState} from "react";

export default function CorpusTextEditor() {
    const [title, setTitle] = useState("")
    const [text, setText] = useState("")
    const [source, setSource] = useState("")
    const router = useRouter()

    const lang = router.query.lang

    function saveCorpusText() {
        addCorpusText(lang, title, text, source)
            .then(r => r.json())
            .then(r => router.push("/corpus/text/" + r.id))
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