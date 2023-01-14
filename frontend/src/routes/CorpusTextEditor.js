import {useState} from "react";
import {addCorpusText} from "../api";
import {useNavigate, useParams} from "react-router";

export default function CorpusTextEditor() {
    const [text, setText] = useState("")
    const params = useParams()
    const navigate = useNavigate()

    function saveCorpusText() {
        addCorpusText(params.lang, text)
            .then(r => r.json())
            .then(r => navigate("/corpus/text/" + r.id))
    }

    return <>
        <textarea rows="10" cols="50" value={text} onChange={e => setText(e.target.value)}/>
        <br/>
        <button onClick={() => saveCorpusText()}>Save</button>
    </>
}

